package org.shirakawatyu.yamibo.novel.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ViewModel Factory，用于创建需要Context的ViewModel
 */
class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ReaderVM::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                ReaderVM(context) as T
            }

            modelClass.isAssignableFrom(FavoriteVM::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                FavoriteVM(context) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

// 在Composable中使用：
// val context = LocalContext.current
// val readerVM: ReaderVM = viewModel(factory = ViewModelFactory(context.applicationContext))