package com.das.bambuwatch.ui

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

const val PRIVACY_POLICY_URL = "https://projects.darcangel.com/bambuwatch/privacy/"

/** Opens the privacy policy in the browser (Google Play requires an in-app link). */
@Composable
fun PrivacyPolicyLink() {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } }) {
        Text("Privacy policy")
    }
}
