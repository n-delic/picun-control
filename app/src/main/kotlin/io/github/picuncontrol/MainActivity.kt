package io.github.picuncontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.picuncontrol.ui.AppViewModel
import io.github.picuncontrol.ui.PicunRoot
import io.github.picuncontrol.ui.theme.PicunControlTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val showDemo = BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)
    setContent {
      PicunControlTheme {
        val vm: AppViewModel = viewModel()
        LaunchedEffect(Unit) {
          if (showDemo) vm.showDemo()
        }
        PicunRoot(vm)
      }
    }
  }
}
