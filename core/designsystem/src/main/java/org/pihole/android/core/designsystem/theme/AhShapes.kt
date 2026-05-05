package org.pihole.android.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Soft brutalist: 14dp cards, 999px pills (CircleShape), 50% avatars.
val AhShape14 = RoundedCornerShape(14.dp)
val AhShape10 = RoundedCornerShape(10.dp)
val AhShapePill = CircleShape
val AhShapeCircle = CircleShape

val AhMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
