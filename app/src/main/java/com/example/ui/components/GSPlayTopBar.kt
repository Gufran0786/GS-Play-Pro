package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MagentaAccent
import com.example.ui.theme.SunsetGold
import com.example.ui.viewmodel.AppTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GSPlayTopBar(
    currentTab: AppTab,
    isVaultUnlocked: Boolean,
    onRefresh: () -> Unit,
    onOpenVault: () -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glowing Pro Badge Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CyanPrimary, MagentaAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "GS Play Pro Logo",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GS PLAY",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MagentaAccent
                        ) {
                            Text(
                                text = "PRO",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = when (currentTab) {
                            AppTab.PHOTOS -> "Photo & Gallery Hub"
                            AppTab.VIDEOS -> "All Videos Player"
                            AppTab.MUSIC -> "Music & Audio Studio"
                            AppTab.STREAM_URL -> "Direct Stream Any Link"
                            AppTab.AI_EXPLORER -> "AI Media Search Engine"
                            AppTab.VAULT -> "Private Vault (Secret Safe)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary
                    )
                }
            }
        },
        actions = {
            // Refresh Media button
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Media",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Vault Lock Indicator / Quick Lock
            IconButton(
                onClick = {
                    if (isVaultUnlocked) onLockVault() else onOpenVault()
                },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(
                        if (isVaultUnlocked) SunsetGold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = "Vault Status",
                    tint = if (isVaultUnlocked) SunsetGold else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkSurface.copy(alpha = 0.95f)
        ),
        modifier = modifier
    )
}
