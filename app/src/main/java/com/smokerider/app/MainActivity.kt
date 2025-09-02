package com.smokerider.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.smokerider.app.ui.admin.AdminHomeScreen
import com.smokerider.app.ui.admin.AggiungiProdottoScreen
import com.smokerider.app.ui.admin.ApprovaRiderScreen
import com.smokerider.app.ui.admin.ProductListScreen
import com.smokerider.app.ui.auth.AuthScreen
import com.smokerider.app.ui.customer.AttesaOrdineScreen
import com.smokerider.app.ui.customer.CustomerHomeScreen
import com.smokerider.app.ui.customer.CustomerPositionScreen
import com.smokerider.app.ui.customer.EffettuaOrdineScreen
import com.smokerider.app.ui.customer.OrderSummaryScreen
import com.smokerider.app.ui.customer.TrackingOrdineScreen
import com.smokerider.app.ui.rider.RiderHomeScreen
import com.smokerider.app.ui.rider.TrackingRiderScreen
import com.smokerider.app.ui.theme.SmokeRiderTheme
import com.smokerider.app.viewmodel.OrderViewModel
import com.smokerider.app.data.repository.FirestoreOrders

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }

        val o = FirebaseApp.getInstance().options
        Log.d("FBCHK", "apiKey=${o.apiKey}, projectId=${o.projectId}, appId=${o.applicationId}")

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        setContent {
            SmokeRiderTheme {
                val navController = rememberNavController()

                // Determina dinamicamente la startDestination:
                // - se non loggato -> "auth"
                // - se loggato -> in base a users/{uid}.role
                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val fbUser = FirebaseAuth.getInstance().currentUser
                    if (fbUser == null) {
                        startDestination = "auth"
                    } else {
                        val role = try {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(fbUser.uid)
                                .get()
                                .await()
                                .getString("role")
                                ?.lowercase()
                        } catch (_: Exception) {
                            null
                        }
                        startDestination = when (role) {
                            "rider" -> "rider/home"
                            "admin" -> "admin/home"
                            else -> "customer/home"
                        }
                    }
                }

                if (startDestination == null) {
                    // Piccolo placeholder mentre carichiamo l’utente/ruolo (evita lampeggi sul login)
                    Box(Modifier.fillMaxSize()) {}
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!
                    ) {
                        // Login/registrazione
                        composable("auth") {
                            AuthScreen(navController = navController)
                        }

                        // Admin
                        composable("admin/home") {
                            AdminHomeScreen(
                                onApprovaRiderClick = { navController.navigate("admin/approvaRider") },
                                onAggiungiProdottoClick = { navController.navigate("admin/aggiungiProdotto") },
                                onGestioneProdottiClick = { navController.navigate("admin/prodotti") },
                                onLogout = {
                                    FirebaseAuth.getInstance().signOut()
                                    navController.navigate("auth") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("admin/approvaRider") { ApprovaRiderScreen() }
                        composable("admin/aggiungiProdotto") { AggiungiProdottoScreen() }
                        composable("admin/prodotti") { ProductListScreen() }

                        // Rider
                        composable("rider/home") {
                            RiderHomeScreen(
                                onOrderClick = { orderId ->
                                    navController.navigate("rider/tracking/$orderId") {
                                        launchSingleTop = true
                                    }
                                },
                                onLogout = {
                                    FirebaseAuth.getInstance().signOut()
                                    navController.navigate("auth") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "rider/tracking/{orderId}",
                            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                            TrackingRiderScreen(
                                orderId = orderId,
                                onGoHome = {
                                    navController.navigate("rider/home") {
                                        popUpTo("rider/home") { inclusive = false }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        //Cliente
                        composable("customer/home") {
                            val ordersRepo = remember { FirestoreOrders() }

                            CustomerHomeScreen(
                                navController = navController,
                                ordersRepo = ordersRepo,
                                onEffettuaOrdineClick = { navController.navigate("customer/ordine") },
                                onLogout = {
                                    FirebaseAuth.getInstance().signOut()
                                    navController.navigate("auth") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("customer/ordine") { backStackEntry ->
                            val parentEntry = remember(backStackEntry) {
                                navController.getBackStackEntry("customer/home")
                            }
                            val orderViewModel: OrderViewModel = viewModel(parentEntry)

                            EffettuaOrdineScreen(
                                navController = navController,
                                orderViewModel = orderViewModel
                            )
                        }
                        composable("customer/position") { backStackEntry ->
                            val user = FirebaseAuth.getInstance().currentUser
                            if (user == null) {
                                navController.navigate("auth") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            } else {
                                val parentEntry = remember(backStackEntry) {
                                    navController.getBackStackEntry("customer/home")
                                }
                                val orderViewModel: OrderViewModel = viewModel(parentEntry)

                                CustomerPositionScreen(
                                    uid = user.uid,
                                    navController = navController,
                                    orderViewModel = orderViewModel,
                                    onConfirm = {
                                        navController.navigate("customer/home") {
                                            popUpTo("customer/home") { inclusive = false }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                        composable(
                            route = "customer/attesa/{orderId}",
                            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                            AttesaOrdineScreen(
                                orderId = orderId,
                                onExpired = {
                                    navController.navigate("customer/home") {
                                        popUpTo("customer/home") { inclusive = false }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onCancelled = {
                                    navController.navigate("customer/home") {
                                        popUpTo("customer/home") { inclusive = false }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onAccepted = { id ->
                                    navController.navigate("customer/tracking/$id") {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(
                            route = "customer/tracking/{orderId}",
                            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                            TrackingOrdineScreen(
                                orderId = orderId,
                                onGoHome = {
                                    navController.navigate("customer/home") {
                                        popUpTo("customer/home") { inclusive = false }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable("customer/summary") { backStackEntry ->
                            val parentEntry = remember(backStackEntry) {
                                navController.getBackStackEntry("customer/home")
                            }
                            val orderViewModel: OrderViewModel = viewModel(parentEntry)
                            val items = orderViewModel.getItems()

                            OrderSummaryScreen(
                                navController = navController,
                                orderViewModel = orderViewModel,
                                items = items,
                                onOrderConfirmed = {
                                    navController.navigate("customer/home") {
                                        popUpTo("customer/home") { inclusive = false }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
