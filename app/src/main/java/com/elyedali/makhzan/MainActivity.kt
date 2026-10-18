package com.elyedali.makhzan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.elyedali.makhzan.data.AppDatabase
import com.elyedali.makhzan.data.ShopDao
import com.elyedali.makhzan.ui.CashScreen
import com.elyedali.makhzan.ui.ProductDetailScreen
import com.elyedali.makhzan.ui.PurchaseScreen
import com.elyedali.makhzan.ui.SaleScreen
import com.elyedali.makhzan.ui.ShopHomeScreen
import com.elyedali.makhzan.ui.ShopsScreen
import com.elyedali.makhzan.ui.StockScreen

sealed interface Route {
    data object Shops : Route
    data class Home(val shopId: Long) : Route
    data class Sale(val shopId: Long) : Route
    data class Purchase(val shopId: Long) : Route
    data class Cash(val shopId: Long) : Route
    data class Stock(val shopId: Long) : Route
    data class ProductDetail(val productId: Long) : Route
}

private val AppColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE1),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF8A5A00),
    secondaryContainer = Color(0xFFFFE3B0),
    background = Color(0xFFF7F8F6),
    surface = Color(0xFFFFFFFF),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(this).dao()
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppRoot(dao)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(dao: ShopDao) {
    var stack by remember { mutableStateOf(listOf<Route>(Route.Shops)) }
    fun go(r: Route) { stack = stack + r }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { back() }

    when (val r = stack.last()) {
        Route.Shops -> ShopsScreen(dao, onOpen = { go(Route.Home(it)) })
        is Route.Home -> ShopHomeScreen(dao, r.shopId, onBack = ::back, go = ::go)
        is Route.Sale -> SaleScreen(dao, r.shopId, onBack = ::back)
        is Route.Purchase -> PurchaseScreen(dao, r.shopId, onBack = ::back)
        is Route.Cash -> CashScreen(dao, r.shopId, onBack = ::back)
        is Route.Stock -> StockScreen(dao, r.shopId, onBack = ::back, onOpen = { go(Route.ProductDetail(it)) })
        is Route.ProductDetail -> ProductDetailScreen(dao, r.productId, onBack = ::back)
    }
}
