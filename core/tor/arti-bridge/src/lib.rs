use anyhow::{anyhow, Context, Result};
use arti_client::config::TorClientConfigBuilder;
use arti_client::config::FS_PERMISSIONS_CHECKS_DISABLE_VAR;
use arti_client::{BootstrapBehavior, TorClient};
use futures::io::{AsyncReadExt, AsyncWriteExt};
use futures::StreamExt;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jlong};
use jni::JNIEnv;
use once_cell::sync::OnceCell;
use std::collections::HashMap;
use std::ffi::CString;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::atomic::{AtomicBool, AtomicI32, Ordering};
use std::sync::{Mutex, Once};
use tor_rtcompat::PreferredRuntime;
use tor_rtcompat::BlockOn;

static INIT: Once = Once::new();
static PREFERRED_RT: OnceCell<PreferredRuntime> = OnceCell::new();
static TOR_CLIENT: Mutex<Option<TorClient<PreferredRuntime>>> = Mutex::new(None);

// Bootstrap status: 0=stopped, 1=starting, 2=ready, 3=failed
static BOOTSTRAP_STATE: AtomicI32 = AtomicI32::new(0);
static BOOTSTRAP_PROGRESS: AtomicI32 = AtomicI32::new(0); // 0..100 (best-effort)
static BOOTSTRAP_SUMMARY: Mutex<String> = Mutex::new(String::new());
static LAST_ERROR: Mutex<String> = Mutex::new(String::new());

static SHUTDOWN: AtomicBool = AtomicBool::new(false);

type StreamId = i64;
static STREAMS: OnceCell<Mutex<HashMap<StreamId, arti_client::DataStream>>> = OnceCell::new();
static NEXT_STREAM_ID: OnceCell<Mutex<StreamId>> = OnceCell::new();

fn init_if_needed() -> Result<()> {
    INIT.call_once(|| {
        let _ = STREAMS.set(Mutex::new(HashMap::new()));
        let _ = NEXT_STREAM_ID.set(Mutex::new(1));
    });
    Ok(())
}

fn preferred_rt() -> Result<&'static PreferredRuntime> {
    init_if_needed()?;
    PREFERRED_RT.get_or_try_init(|| PreferredRuntime::create().context("PreferredRuntime::create"))
}

fn set_last_error(message: String) {
    if let Ok(mut g) = LAST_ERROR.lock() {
        *g = message;
    }
}

fn clear_last_error() {
    if let Ok(mut g) = LAST_ERROR.lock() {
        g.clear();
    }
}

fn set_bootstrap_summary(summary: String) {
    if let Ok(mut g) = BOOTSTRAP_SUMMARY.lock() {
        *g = summary;
    }
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeInit(
    mut env: JNIEnv,
    _clazz: JClass,
    data_dir: JString,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        init_if_needed()?;

        // Android app-private dirs are often group/world readable; Arti's fs mistrust can be strict.
        // This matches Arti's documented escape hatch for constrained environments.
        std::env::set_var(FS_PERMISSIONS_CHECKS_DISABLE_VAR, "1");

        let data_dir: String = env.get_string(&data_dir)?.into();
        let state_dir = std::path::PathBuf::from(&data_dir).join("state");
        let cache_dir = std::path::PathBuf::from(&data_dir).join("cache");
        std::fs::create_dir_all(&state_dir).context("create state dir")?;
        std::fs::create_dir_all(&cache_dir).context("create cache dir")?;

        // Stash chosen dirs for nativeStart (keeps JNI surface small).
        // Android is effectively single-process for the app UID; this avoids threading new JNI args.
        std::env::set_var("ANDHOLE_ARTI_STATE_DIR", state_dir.to_string_lossy().as_ref());
        std::env::set_var("ANDHOLE_ARTI_CACHE_DIR", cache_dir.to_string_lossy().as_ref());

        Ok(())
    }))
    .unwrap_or_else(|_| Err(anyhow!("nativeInit panicked")));

    // JNI throws are handled in nativeStart; init failures are rare.
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeStart(
    mut env: JNIEnv,
    _clazz: JClass,
) {
    let res = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        init_if_needed()?;
        clear_last_error();
        SHUTDOWN.store(false, Ordering::SeqCst);

        BOOTSTRAP_STATE.store(1, Ordering::SeqCst);
        BOOTSTRAP_PROGRESS.store(0, Ordering::SeqCst);
        set_bootstrap_summary(String::new());

        let rt = preferred_rt().context("preferred runtime")?;

        let state_dir = std::env::var("ANDHOLE_ARTI_STATE_DIR")
            .map_err(|_| anyhow!("missing ANDHOLE_ARTI_STATE_DIR; call nativeInit"))?;
        let cache_dir = std::env::var("ANDHOLE_ARTI_CACHE_DIR")
            .map_err(|_| anyhow!("missing ANDHOLE_ARTI_CACHE_DIR; call nativeInit"))?;

        // With `onion-service-client`, connections to `.onion` are still rejected unless this is set
        // (see arti-client `ClientAddrConfig::allow_onion_addrs`, default false in the builder).
        let mut cfg_builder = TorClientConfigBuilder::from_directories(state_dir, cache_dir);
        cfg_builder.address_filter().allow_onion_addrs(true);
        let cfg = cfg_builder
            .build()
            .context("build TorClientConfig")?;

        // Replace any previous client.
        {
            let mut slot = TOR_CLIENT.lock().map_err(|_| anyhow!("client mutex poisoned"))?;
            *slot = None;
        }

        let client = rt
            .block_on(async {
                TorClient::with_runtime(rt.clone())
                    .config(cfg)
                    .bootstrap_behavior(BootstrapBehavior::Manual)
                    .create_unbootstrapped()
                    .map_err(|e| anyhow!(e.to_string()))
            })
            .context("create_unbootstrapped")?;

        // Explicit bootstrap to completion, while consuming bootstrap events for progress UI.
        let bootstrap_res = rt.block_on(async {
            use std::sync::Arc;
            let done = Arc::new(AtomicBool::new(false));

            let client_for_bootstrap = client.clone();
            let done_for_bootstrap = Arc::clone(&done);
            let bootstrap_task = async move {
                let res = client_for_bootstrap
                    .bootstrap()
                    .await
                    .map_err(|e| anyhow!(e.to_string()));
                done_for_bootstrap.store(true, Ordering::SeqCst);
                res
            };

            let mut events = client.bootstrap_events();
            let done_for_progress = Arc::clone(&done);
            let progress_task = async move {
                while !done_for_progress.load(Ordering::SeqCst) {
                    if SHUTDOWN.load(Ordering::SeqCst) {
                        return Err(anyhow!("shutdown requested during bootstrap"));
                    }
                    match events.next().await {
                        Some(st) => {
                            let pct = (st.as_frac() * 100.0).round().clamp(0.0, 100.0) as i32;
                            BOOTSTRAP_PROGRESS.store(pct, Ordering::SeqCst);
                            set_bootstrap_summary(st.to_string());
                        }
                        None => {
                            // If the stream ends unexpectedly, just backoff a little.
                            tokio::time::sleep(std::time::Duration::from_millis(50)).await;
                        }
                    }
                }
                Ok::<(), anyhow::Error>(())
            };

            let (bootstrap_res, progress_res) = tokio::join!(bootstrap_task, progress_task);
            progress_res?;
            bootstrap_res
        });
        if let Err(e) = bootstrap_res {
            let msg = format!("{e:#}");
            set_last_error(msg.clone());
            BOOTSTRAP_STATE.store(3, Ordering::SeqCst);
            return Err(anyhow!(msg));
        }

        {
            let mut slot = TOR_CLIENT.lock().map_err(|_| anyhow!("client mutex poisoned"))?;
            *slot = Some(client);
        }

        BOOTSTRAP_STATE.store(2, Ordering::SeqCst);
        BOOTSTRAP_PROGRESS.store(100, Ordering::SeqCst);
        Ok(())
    }))
    .unwrap_or_else(|_| Err(anyhow!("nativeStart panicked")));

    if let Err(e) = res {
        BOOTSTRAP_STATE.store(3, Ordering::SeqCst);
        set_last_error(format!("{e:#}"));
        let _ = env.throw_new("java/lang/RuntimeException", format!("nativeStart failed: {e:#}"));
    }
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeBootstrapState(
    _env: JNIEnv,
    _clazz: JClass,
) -> i32 {
    BOOTSTRAP_STATE.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeBootstrapProgress(
    _env: JNIEnv,
    _clazz: JClass,
) -> i32 {
    BOOTSTRAP_PROGRESS.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeBootstrapSummary(
    env: JNIEnv,
    _clazz: JClass,
) -> jni::sys::jstring {
    let summary = BOOTSTRAP_SUMMARY
        .lock()
        .map(|g| g.clone())
        .unwrap_or_default();
    let c = CString::new(summary).unwrap_or_else(|_| CString::new("").unwrap());
    env.new_string(c.to_string_lossy().as_ref())
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeLastError(
    env: JNIEnv,
    _clazz: JClass,
) -> jni::sys::jstring {
    let msg = LAST_ERROR.lock().map(|g| g.clone()).unwrap_or_default();
    let c = CString::new(msg).unwrap_or_else(|_| CString::new("").unwrap());
    env.new_string(c.to_string_lossy().as_ref())
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeOpenStream(
    mut env: JNIEnv,
    _clazz: JClass,
    host: JString,
    port: i32,
) -> jlong {
    let res = catch_unwind(AssertUnwindSafe(|| -> Result<StreamId> {
        init_if_needed()?;
        let host: String = env.get_string(&host)?.into();
        let port_u16: u16 = port.try_into().map_err(|_| anyhow!("invalid port"))?;
        let rt = preferred_rt()?;
        let client = {
            let slot = TOR_CLIENT.lock().map_err(|_| anyhow!("client mutex poisoned"))?;
            slot.as_ref()
                .ok_or_else(|| anyhow!("Tor client not started"))?
                .clone()
        };

        let stream = rt
            .block_on(async { client.connect((host.as_str(), port_u16)).await })
            .context("tor_client.connect")?;

        let id = {
            let mut next = NEXT_STREAM_ID.get().unwrap().lock().unwrap();
            let id = *next;
            *next += 1;
            id
        };
        STREAMS.get().unwrap().lock().unwrap().insert(id, stream);
        Ok(id)
    }))
    .unwrap_or_else(|_| Err(anyhow!("nativeOpenStream panicked")));

    match res {
        Ok(id) => id as jlong,
        Err(e) => {
            // Stream-open failures are transport-level errors, not bootstrap failures.
            // Leave BOOTSTRAP_STATE untouched so one transient connect miss does not latch
            // the whole runtime into "failed" while the Tor client remains usable.
            let _ = env.throw_new("java/io/IOException", format!("openStream failed: {e:#}"));
            0
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeWrite(
    mut env: JNIEnv,
    _clazz: JClass,
    stream_id: jlong,
    bytes: jbyteArray,
) {
    let res = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        init_if_needed()?;
        let rt = preferred_rt()?;
        // `bytes` is a raw JNI handle (`jbyteArray`); wrap it for the safe JNIEnv helpers.
        let bytes = unsafe { JByteArray::from_raw(bytes) };
        let mut buf = env.convert_byte_array(bytes)?;
        let mut streams = STREAMS.get().unwrap().lock().unwrap();
        let stream = streams
            .get_mut(&(stream_id as StreamId))
            .ok_or_else(|| anyhow!("unknown stream id"))?;
        rt.block_on(async {
            stream.write_all(&mut buf).await?;
            stream.flush().await?;
            Ok::<(), std::io::Error>(())
        })
        .context("write_all/flush")?;
        Ok(())
    }))
    .unwrap_or_else(|_| Err(anyhow!("nativeWrite panicked")));

    if let Err(e) = res {
        let _ = env.throw_new("java/io/IOException", format!("write failed: {e:#}"));
    }
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeRead(
    mut env: JNIEnv,
    _clazz: JClass,
    stream_id: jlong,
    length: i32,
) -> jbyteArray {
    let res = catch_unwind(AssertUnwindSafe(|| -> Result<Vec<u8>> {
        init_if_needed()?;
        let length: usize = length.try_into().map_err(|_| anyhow!("invalid length"))?;
        let rt = preferred_rt()?;
        let mut streams = STREAMS.get().unwrap().lock().unwrap();
        let stream = streams
            .get_mut(&(stream_id as StreamId))
            .ok_or_else(|| anyhow!("unknown stream id"))?;
        let mut buf = vec![0u8; length];
        rt.block_on(async {
            stream.read_exact(&mut buf).await?;
            Ok::<(), std::io::Error>(())
        })
        .context("read_exact")?;
        Ok(buf)
    }))
    .unwrap_or_else(|_| Err(anyhow!("nativeRead panicked")));

    match res {
        Ok(buf) => env
            .byte_array_from_slice(&buf)
            .map(|a| a.into_raw())
            .unwrap_or_else(|_| env.new_byte_array(0).unwrap().into_raw()),
        Err(e) => {
            let _ = env.throw_new("java/io/IOException", format!("read failed: {e:#}"));
            env.new_byte_array(0).unwrap().into_raw()
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeCloseStream(
    mut env: JNIEnv,
    _clazz: JClass,
    stream_id: jlong,
) {
    let res = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        init_if_needed()?;
        let rt = preferred_rt()?;
        let mut stream = {
            let mut streams = STREAMS.get().unwrap().lock().unwrap();
            streams.remove(&(stream_id as StreamId))
        }
        .ok_or_else(|| anyhow!("unknown stream id"))?;

        rt.block_on(async {
            stream.close().await?;
            Ok::<(), std::io::Error>(())
        })
        .context("stream.close")?;
        Ok(())
    }))
    .unwrap_or_else(|_| Err(anyhow!("nativeCloseStream panicked")));

    if let Err(e) = res {
        let _ = env.throw_new("java/lang/RuntimeException", format!("closeStream failed: {e:#}"));
    }
}

#[no_mangle]
pub extern "C" fn Java_org_pihole_android_core_tor_runtime_ArtiJni_nativeShutdown(
    mut env: JNIEnv,
    _clazz: JClass,
) {
    let res = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        init_if_needed()?;
        SHUTDOWN.store(true, Ordering::SeqCst);

        if let Some(m) = STREAMS.get() {
            let mut streams = m.lock().unwrap();
            // Best-effort close streams synchronously.
            if let Ok(rt) = preferred_rt() {
                for (_id, mut s) in streams.drain() {
                    let _ = rt.block_on(async {
                        let _ = s.close().await;
                    });
                }
            } else {
                streams.clear();
            }
        }

        if let Ok(mut slot) = TOR_CLIENT.lock() {
            *slot = None;
        }

        BOOTSTRAP_STATE.store(0, Ordering::SeqCst);
        BOOTSTRAP_PROGRESS.store(0, Ordering::SeqCst);
        set_bootstrap_summary(String::new());
        SHUTDOWN.store(false, Ordering::SeqCst);
        Ok(())
    }))
    .unwrap_or_else(|_| Err(anyhow!("nativeShutdown panicked")));

    if let Err(e) = res {
        let _ = env.throw_new("java/lang/RuntimeException", format!("shutdown failed: {e:#}"));
    }
}
