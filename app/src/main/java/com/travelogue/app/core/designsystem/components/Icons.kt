package com.travelogue.app.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.travelogue.app.core.designsystem.theme.Travel
import com.travelogue.app.data.db.entity.*

/** Icon registry for expense categories. Custom categories pick one of [keys]. */
object CategoryIcons {
    private val map: Map<String, ImageVector> = linkedMapOf(
        "transport" to Icons.Rounded.DirectionsCar,
        "hotel" to Icons.Rounded.Hotel,
        "food" to Icons.Rounded.LunchDining,
        "restaurant" to Icons.Rounded.Restaurant,
        "activity" to Icons.Rounded.Hiking,
        "shopping" to Icons.Rounded.ShoppingBag,
        "ticket" to Icons.Rounded.ConfirmationNumber,
        "fuel" to Icons.Rounded.LocalGasStation,
        "parking" to Icons.Rounded.LocalParking,
        "medical" to Icons.Rounded.MedicalServices,
        "flight" to Icons.Rounded.Flight,
        "train" to Icons.Rounded.Train,
        "coffee" to Icons.Rounded.LocalCafe,
        "drinks" to Icons.Rounded.LocalBar,
        "gift" to Icons.Rounded.CardGiftcard,
        "spa" to Icons.Rounded.Spa,
        "museum" to Icons.Rounded.Museum,
        "beach" to Icons.Rounded.BeachAccess,
        "phone" to Icons.Rounded.PhoneAndroid,
        "laundry" to Icons.Rounded.LocalLaundryService,
        "tips" to Icons.Rounded.VolunteerActivism,
        "other" to Icons.Rounded.Category,
    )
    val keys: List<String> get() = map.keys.toList()
    fun forKey(key: String?): ImageVector = map[key] ?: Icons.Rounded.Category
}

/** Colours offered for custom categories. */
val CategoryColorChoices: List<Long> = listOf(
    0xFFC5603C, 0xFF2F5D62, 0xFFC99A2E, 0xFF7D8F5E, 0xFF7A4E6B, 0xFF52687D, 0xFFD98B84, 0xFFB79C7A, 0xFF8C6A4F, 0xFF4E8A86, 0xFFB3412E, 0xFFA39A90,
)

fun ExpenseCategoryEntity?.colorValue(): Color = Color(this?.color ?: 0xFFA39A90)
fun ExpenseCategoryEntity?.icon(): ImageVector = CategoryIcons.forKey(this?.iconKey)

val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.BEACH -> Icons.Rounded.BeachAccess
        PlaceCategory.RESTAURANT -> Icons.Rounded.Restaurant
        PlaceCategory.ATTRACTION -> Icons.Rounded.CameraAlt
        PlaceCategory.HISTORICAL -> Icons.Rounded.AccountBalance
        PlaceCategory.SHOPPING -> Icons.Rounded.ShoppingBag
        PlaceCategory.NATURE -> Icons.Rounded.Park
        PlaceCategory.ADVENTURE -> Icons.Rounded.Kayaking
        PlaceCategory.OTHER -> Icons.Rounded.Place
    }

val BookingType.icon: ImageVector
    get() = when (this) {
        BookingType.FLIGHT -> Icons.Rounded.Flight
        BookingType.TRAIN -> Icons.Rounded.Train
        BookingType.BUS -> Icons.Rounded.DirectionsBus
        BookingType.HOTEL -> Icons.Rounded.Hotel
        BookingType.ACTIVITY -> Icons.Rounded.LocalActivity
        BookingType.FERRY -> Icons.Rounded.DirectionsBoat
        BookingType.OTHER -> Icons.Rounded.ConfirmationNumber
    }

val ContactType.icon: ImageVector
    get() = when (this) {
        ContactType.HOTEL_RECEPTION -> Icons.Rounded.RoomService
        ContactType.HOTEL_OWNER -> Icons.Rounded.Hotel
        ContactType.TAXI_DRIVER -> Icons.Rounded.LocalTaxi
        ContactType.AUTO_DRIVER -> Icons.Rounded.ElectricRickshaw
        ContactType.TOUR_GUIDE -> Icons.Rounded.Tour
        ContactType.TRAVEL_AGENT -> Icons.Rounded.SupportAgent
        ContactType.EMERGENCY -> Icons.Rounded.Emergency
        ContactType.RESTAURANT -> Icons.Rounded.Restaurant
        ContactType.OTHER -> Icons.Rounded.Person
    }

val PaymentMethod.icon: ImageVector
    get() = when (this) {
        PaymentMethod.CASH -> Icons.Rounded.Payments
        PaymentMethod.CARD -> Icons.Rounded.CreditCard
        PaymentMethod.UPI -> Icons.Rounded.QrCode2
        PaymentMethod.BANK_TRANSFER -> Icons.Rounded.AccountBalance
        PaymentMethod.WALLET -> Icons.Rounded.AccountBalanceWallet
        PaymentMethod.OTHER -> Icons.Rounded.MoreHoriz
    }

@Composable
@ReadOnlyComposable
fun TripStatus.color(): Color = when (this) {
    TripStatus.PLANNING -> Travel.colors.inkSoft
    TripStatus.UPCOMING -> Travel.colors.secondary
    TripStatus.ONGOING -> Travel.colors.gold
    TripStatus.COMPLETED -> Travel.colors.sage
}

@Composable
@ReadOnlyComposable
fun PaymentStatus.color(): Color = when (this) {
    PaymentStatus.PAID -> Travel.colors.success
    PaymentStatus.PARTIALLY_PAID -> Travel.colors.secondary
    PaymentStatus.PENDING, PaymentStatus.PAY_AT_PROPERTY -> Travel.colors.gold
    PaymentStatus.REFUNDED -> Travel.colors.inkSoft
}

@Composable
@ReadOnlyComposable
fun ActivityStatus.color(): Color = when (this) {
    ActivityStatus.PLANNED -> Travel.colors.secondary
    ActivityStatus.COMPLETED -> Travel.colors.success
    ActivityStatus.SKIPPED -> Travel.colors.inkFaint
}
