package io.github.picuncontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PicunRoot(vm: AppViewModel = viewModel()) {
  val state by vm.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }

  LaunchedEffect(vm) {
    vm.messages.collect { message -> snackbar.showSnackbar(message) }
  }

  BackHandler(enabled = state.activeAddress != null) {
    if (state.showAdvanced) vm.setAdvanced(false) else vm.disconnect()
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
  ) { inner ->
    Surface(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
      when {
        state.activeAddress == null -> ScanScreen(state, vm)
        state.showAdvanced -> AdvancedScreen(state, vm)
        else -> DeviceScreen(state, vm)
      }
    }
  }
}
