package app.k56.player.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Small helper that keeps screen-level ViewModel construction explicit instead
 * of pulling in an annotation processor.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: () -> VM): VM =
    viewModel<VM>(factory = viewModelFactory { initializer { create() } })
