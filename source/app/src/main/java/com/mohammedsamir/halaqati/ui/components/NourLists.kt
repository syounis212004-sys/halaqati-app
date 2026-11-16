package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items as foundationItems
import androidx.compose.foundation.lazy.itemsIndexed as foundationItemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * RC98: drop-in replacements for the foundation `items` / `itemsIndexed` that mark everything drawn inside a list row as
 * [NourDepth.Row], so the unified cards in a long list are nearly flat while a lone card (an `item { }` block) keeps the
 * medium tier. Same parameters and behaviour as the foundation functions; the only addition is the depth provider.
 */
fun <T> LazyListScope.items(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    contentType: (item: T) -> Any? = { null },
    itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) = foundationItems(items, key, contentType) { item ->
    CompositionLocalProvider(LocalNourDepth provides NourDepth.Row) { itemContent(item) }
}

// items(count: Int, ...) is a MEMBER of LazyListScope and always wins over an extension, so it cannot be wrapped here;
// the few index-based lists keep the Card tier.

fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    key: ((index: Int, item: T) -> Any)? = null,
    contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) = foundationItemsIndexed(items, key, contentType) { index, item ->
    CompositionLocalProvider(LocalNourDepth provides NourDepth.Row) { itemContent(index, item) }
}
