package com.elyedali.makhzan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import com.elyedali.makhzan.auth.Auth
import com.elyedali.makhzan.activation.Activation
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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
import com.elyedali.makhzan.activation.ActivationRequest
import com.elyedali.makhzan.data.Plans
import com.elyedali.makhzan.data.Shop
import com.elyedali.makhzan.data.ShopDao
import kotlinx.coroutines.launch
import java.util.UUID
import com.elyedali.makhzan.i18n.tr

@Composable
fun ShopsScreen(
    dao: ShopDao,
    onOpen: (Long) -> Unit,
    driveOk: Boolean,
    requestDrive: () -> Unit,
    onAdminLink: (ActivationRequest) -> Unit,
    onAdminPanel: () -> Unit,
) {
    val shops by dao.shops().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    Scaffold(
        topBar = {
            AppTopBar(tr("محلاتي التجارية"), actions = {
                if (Auth.isAdmin()) {
                    IconButton(onClick = onAdminPanel) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = tr("لوحة المدير"))
                    }
                }
                IconButton(onClick = { showLang = true }) { Icon(Icons.Default.Language, contentDescription = tr("لغة التطبيق")) }
                IconButton(onClick = { showAccount = true }) { Icon(Icons.Default.AccountCircle, contentDescription = tr("حسابي")) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(tr("إنشاء محل تجاري")) },
            )
        },
    ) { pad ->
      Column(Modifier.padding(pad).fillMaxSize()) {
        if (!driveOk) {
            Card(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("الحفظ التلقائي على Google Drive غير مفعّل بعد"),
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = requestDrive) { Text(tr("تفعيل الآن")) }
                }
            }
        }
        if (shops.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    tr("لا توجد محلات بعد.\nاضغط «إنشاء محل تجاري» للبدء."),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shops, key = { it.id }) { shop ->
                    Card(onClick = { onOpen(shop.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Storefront, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(Plans.label(shop, now), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
      }
    }

    if (showAccount) AccountDialog(
        onDismiss = { showAccount = false },
        onEnableDrive = { showAccount = false; requestDrive() },
        onAdminLink = { showAccount = false; onAdminLink(it) },
    )
    if (showLang) LanguageDialog(onDismiss = { showLang = false })

    if (showAdd) {
        TextInputDialog(
            title = tr("محل تجاري جديد"),
            label = tr("اسم المحل"),
            confirmText = tr("إنشاء"),
            onDismiss = { showAdd = false },
            onConfirm = { name ->
                showAdd = false
                scope.launch {
                    val newShop = Shop(name = name, createdAt = System.currentTimeMillis(), remoteId = UUID.randomUUID().toString())
                    val id = dao.insertShop(newShop)
                    Activation.registerShop(newShop.copy(id = id))
                }
            },
        )
    }
}
