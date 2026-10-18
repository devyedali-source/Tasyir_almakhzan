package com.elyedali.makhzan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elyedali.makhzan.data.Plans
import com.elyedali.makhzan.data.Shop
import com.elyedali.makhzan.data.ShopDao
import kotlinx.coroutines.launch

@Composable
fun ShopsScreen(dao: ShopDao, onOpen: (Long) -> Unit) {
    val shops by dao.shops().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    Scaffold(
        topBar = { AppTopBar("محلاتي التجارية") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("إنشاء محل تجاري") },
            )
        },
    ) { pad ->
        if (shops.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "لا توجد محلات بعد.\nاضغط «إنشاء محل تجاري» للبدء.",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shops, key = { it.id }) { shop ->
                    Card(onClick = { onOpen(shop.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Storefront, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(shop.name, style = MaterialTheme.typography.titleMedium)
                                Text(Plans.label(shop, now), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            title = "محل تجاري جديد",
            label = "اسم المحل",
            confirmText = "إنشاء",
            onDismiss = { showAdd = false },
            onConfirm = { name ->
                showAdd = false
                scope.launch { dao.insertShop(Shop(name = name, createdAt = System.currentTimeMillis())) }
            },
        )
    }
}
