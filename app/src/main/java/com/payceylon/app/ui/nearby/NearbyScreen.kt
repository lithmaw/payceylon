package com.payceylon.app.ui.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.payceylon.app.R
import com.payceylon.app.data.ApiException
import com.payceylon.app.ui.components.bounceClick
import com.payceylon.app.ui.components.showError
import com.payceylon.app.ui.components.tap
import com.payceylon.app.ui.theme.InterFontFamily
import com.payceylon.app.ui.theme.LocalWalletColors
import com.payceylon.app.ui.theme.PayCeylonPrimary
import com.payceylon.app.ui.wallet.CircleBackButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Kurunegala clock tower: where the map starts, and the fallback without a location fix. */
private val Kurunegala = GeoPoint(7.4867, 80.3647)
private const val SEARCH_RADIUS_METERS = 3000

/** Panning further than this from the last search offers "Search this area". */
private const val RESEARCH_DISTANCE_METERS = 1200.0

private val AtmColor = Color(0xFF1F1F23)
private val BankColor = Color(0xFF2F6FED)
private val UserDotColor = Color(0xFF2F80ED)

private val PlaceKind.color: Color
    get() = when (this) {
        PlaceKind.Agent -> PayCeylonPrimary
        PlaceKind.Atm -> AtmColor
        PlaceKind.Bank -> BankColor
    }

@get:DrawableRes
private val PlaceKind.icon: Int
    get() = when (this) {
        PlaceKind.Agent -> R.drawable.ic_store
        PlaceKind.Atm -> R.drawable.ic_cash
        PlaceKind.Bank -> R.drawable.ic_withdraw
    }

/**
 * Map of ATMs, bank branches and PayCeylon agents around the user (GPS), with a list sorted by
 * distance. Without location permission or a fix it shows Kurunegala town.
 */
@Composable
fun NearbyScreen(
    /** Places around a point; throws ApiException when the server can't be reached. */
    loadPlaces: suspend (latitude: Double, longitude: Double, radiusMeters: Int) -> NearbyPlaces,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalWalletColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locating by remember { mutableStateOf(true) }
    var searchCentre by remember { mutableStateOf<GeoPoint?>(null) }
    var places by remember { mutableStateOf(emptyList<NearbyPlace>()) }
    var banksAvailable by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var movedAway by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(PlaceFilter.All) }


    val visible = places.filter(filter::matches)

    suspend fun search(centre: GeoPoint) {
        searchCentre = centre
        movedAway = false
        loading = true
        error = null
        try {
            val result = loadPlaces(centre.latitude, centre.longitude, SEARCH_RADIUS_METERS)
            places = result.places
            banksAvailable = result.banksAvailable
        } catch (e: ApiException) {
            error = e.message
        } finally {
            loading = false
        }
    }

    suspend fun locateAndSearch() {
        locating = true
        userLocation = if (hasLocationPermission(context)) currentLocation(context) else null
        locating = false
        search(userLocation ?: Kurunegala)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { scope.launch { locateAndSearch() } }

    fun askForLocation() {
        if (hasLocationPermission(context)) {
            // Permission is there but no fix: location is probably switched off.
            try {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                scope.launch { locateAndSearch() }
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            locateAndSearch()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Coming back from the system location settings retries the fix.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    if (userLocation == null && !locating && hasLocationPermission(context)) {
                        scope.launch { locateAndSearch() }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    LaunchedEffect(mapView, searchCentre) {
        val centre = searchCentre ?: return@LaunchedEffect
        mapView?.controller?.animateTo(centre, 15.0, 600L)
    }

    // Markers: redrawn when the list, filter or the user's position changes.
    val icons = remember { mutableMapOf<PlaceKind, BitmapDrawable>() }
    LaunchedEffect(mapView, visible, userLocation) {
        val map = mapView ?: return@LaunchedEffect
        map.overlays.removeAll { it is Marker }
        visible.forEach { place ->
            map.overlays.add(
                Marker(map).apply {
                    position = GeoPoint(place.latitude, place.longitude)
                    icon = icons.getOrPut(place.kind) { placePin(context, place.kind) }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = place.name
                    infoWindow = null
                    setOnMarkerClickListener { _, _ ->
                        openDirections(context, place)
                        true
                    }
                },
            )
        }
        // The user's dot goes over everything, so a bank at their door doesn't hide it.
        userLocation?.let { location ->
            map.overlays.add(
                Marker(map).apply {
                    position = location
                    icon = userDot(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> true }
                },
            )
        }
        map.invalidate()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f),
        ) {
            AndroidView(
                factory = { viewContext ->
                    Configuration.getInstance().apply {
                        // OpenStreetMap's tile policy requires an app-specific user agent.
                        userAgentValue = viewContext.packageName
                        osmdroidBasePath = File(viewContext.cacheDir, "osmdroid")
                        osmdroidTileCache = File(osmdroidBasePath, "tiles")
                    }
                    var userPanned = false
                    MapView(viewContext).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        isTilesScaledToDpi = true
                        minZoomLevel = 6.0
                        maxZoomLevel = 19.0
                        controller.setZoom(14.5)
                        controller.setCenter(Kurunegala)
                        setOnTouchListener { view, event ->
                            if (event.action == MotionEvent.ACTION_MOVE) userPanned = true
                            if (event.action == MotionEvent.ACTION_UP) view.performClick()
                            false
                        }
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                val centre = searchCentre
                                if (userPanned && centre != null) {
                                    movedAway = GeoPoint(mapCenter).distanceToAsDouble(centre) > RESEARCH_DISTANCE_METERS
                                }
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean = false
                        })
                    }.also { mapView = it }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                CircleBackButton(
                    onClick = onBack,
                    background = Color.White,
                    iconTint = Color.Black,
                    modifier = Modifier.shadow(6.dp, CircleShape),
                )
                MapPill(
                    text = "Nearby",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            AnimatedVisibility(
                visible = movedAway && !loading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            ) {
                MapPill(
                    text = "Search this area",
                    color = PayCeylonPrimary,
                    onClick = {
                        val centre = mapView?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: return@MapPill
                        scope.launch { search(centre) }
                    },
                )
            }

            // Back to the user's position (or ask for it).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 40.dp)
                    .shadow(6.dp, CircleShape)
                    .size(44.dp)
                    .background(Color.White, CircleShape)
                    .tap {
                        val location = userLocation
                        if (location == null) {
                            askForLocation()
                        } else if (searchCentre?.distanceToAsDouble(location)?.let { it > RESEARCH_DISTANCE_METERS } == true) {
                            scope.launch { search(location) }
                        } else {
                            mapView?.controller?.animateTo(location, max(mapView?.zoomLevelDouble ?: 15.0, 15.0), 500L)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = "My location",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (userLocation != null) UserDotColor else Color.Black),
                )
            }

            Text(
                text = "© OpenStreetMap contributors",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 32.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.Black.copy(alpha = 0.7f),
                fontFamily = InterFontFamily,
                fontSize = 10.sp,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.53f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.page)
                .navigationBarsPadding()
                .padding(top = 20.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = when {
                        locating -> "Finding your location…"
                        userLocation != null && searchCentre == userLocation -> "Near you"
                        searchCentre == Kurunegala -> "Kurunegala town"
                        else -> "This area"
                    },
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "ATMs, banks and PayCeylon agents within ${SEARCH_RADIUS_METERS / 1000} km",
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (userLocation == null && !locating) {
                    Spacer(Modifier.height(12.dp))
                    Notice(
                        text = "Turn on location to see places near you.",
                        action = "Allow",
                        onAction = ::askForLocation,
                    )
                }
                if (!banksAvailable && !loading) {
                    Spacer(Modifier.height(12.dp))
                    Notice(text = "Bank and ATM locations can't be loaded right now. Showing PayCeylon agents only.")
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaceFilter.entries.forEach { option ->
                    FilterChip(
                        label = "${option.label} ${places.count(option::matches)}",
                        selected = option == filter,
                        onClick = { filter = option },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                loading && places.isEmpty() || locating -> CenteredMessage {
                    CircularProgressIndicator(color = PayCeylonPrimary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                }
                error != null && places.isEmpty() -> CenteredMessage {
                    Text(error.orEmpty(), color = colors.contentSecondary, fontFamily = InterFontFamily, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Try again",
                        modifier = Modifier.tap { scope.launch { search(searchCentre ?: Kurunegala) } },
                        color = PayCeylonPrimary,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                visible.isEmpty() -> CenteredMessage {
                    Text(
                        text = "Nothing found within ${SEARCH_RADIUS_METERS / 1000} km.",
                        color = colors.contentSecondary,
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { place ->
                        PlaceRow(
                            place = place,
                            onClick = { openDirections(context, place) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: NearbyPlace,
    onClick: () -> Unit,
) {
    val colors = LocalWalletColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.card)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(place.kind.color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(place.kind.icon),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    colorFilter = ColorFilter.tint(place.kind.color),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = place.name,
                    color = colors.content,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(place.kindLabel, place.address).filter { it.isNotBlank() }.joinToString(" · "),
                    color = colors.contentSecondary,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDistance(place.distanceMeters),
                color = colors.content,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalWalletColors.current
    Text(
        text = label,
        modifier = Modifier
            .tap(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PayCeylonPrimary else colors.card)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.White else colors.content,
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** White pill floating on the map; [color] tints the text. */
@Composable
private fun MapPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = text,
        modifier = modifier
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .shadow(6.dp, RoundedCornerShape(50))
            .background(Color.White, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = color,
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Notice(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = LocalWalletColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = colors.contentSecondary,
            fontFamily = InterFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        if (action != null) {
            Text(
                text = action,
                modifier = Modifier.tap(onClick = onAction),
                color = PayCeylonPrimary,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/** A fresh GPS/network fix (up to 10 s), else the last known one; null without either. */
@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): GeoPoint? = try {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellation = CancellationTokenSource()
    val location = withTimeoutOrNull(10_000) {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await(cancellation)
    } ?: client.lastLocation.await()
    location?.let { GeoPoint(it.latitude, it.longitude) }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    // No Google Play services, location switched off, or permission revoked meanwhile.
    null
}

/** Walking directions in Google Maps (the app if installed, else the website). */
private fun openDirections(context: Context, place: NearbyPlace) {
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}&travelmode=walking",
    )
    val maps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(maps)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            showError(context, "No maps app or browser found.")
        }
    }
}

/** Round pin in the place's colour with a white ring and glyph. */
private fun placePin(context: Context, kind: PlaceKind): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius, paint)
    paint.color = kind.color.toArgb()
    canvas.drawCircle(radius, radius, radius - 2.5f * density, paint)
    ContextCompat.getDrawable(context, kind.icon)?.mutate()?.let { glyph ->
        glyph.setTint(android.graphics.Color.WHITE)
        val glyphSize = (size * 0.5f).roundToInt()
        val offset = (size - glyphSize) / 2
        glyph.setBounds(offset, offset, offset + glyphSize, offset + glyphSize)
        glyph.draw(canvas)
    }
    return BitmapDrawable(context.resources, bitmap)
}

/** Blue "you are here" dot with a soft halo. */
private fun userDot(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (36 * density).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val centre = size / 2f
    paint.color = UserDotColor.copy(alpha = 0.2f).toArgb()
    canvas.drawCircle(centre, centre, centre, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(centre, centre, 9 * density, paint)
    paint.color = UserDotColor.toArgb()
    canvas.drawCircle(centre, centre, 6.5f * density, paint)
    return BitmapDrawable(context.resources, bitmap)
}
