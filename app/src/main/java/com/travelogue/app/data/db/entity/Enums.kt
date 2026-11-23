package com.travelogue.app.data.db.entity

enum class TripStatus(val label: String) { PLANNING("Planning"), UPCOMING("Upcoming"), ONGOING("Ongoing"), COMPLETED("Completed") }

enum class TravelType(val label: String) { PERSONAL("Personal"), COUPLE("Couple"), FAMILY("Family"), FRIENDS("Friends"), SOLO("Solo") }

enum class SplitType(val label: String) { NONE("Not split"), EQUAL("Split equally"), CUSTOM("Custom split") }

enum class PaymentMethod(val label: String) { CASH("Cash"), CARD("Card"), UPI("UPI"), BANK_TRANSFER("Bank transfer"), WALLET("Wallet"), OTHER("Other") }

enum class PaymentStatus(val label: String) { PAID("Paid"), PARTIALLY_PAID("Partially paid"), PENDING("Pending"), PAY_AT_PROPERTY("Pay at property"), REFUNDED("Refunded") }

enum class ActivityStatus(val label: String) { PLANNED("Planned"), COMPLETED("Completed"), SKIPPED("Skipped") }

enum class PlaceCategory(val label: String) {
    BEACH("Beach"), RESTAURANT("Restaurant"), ATTRACTION("Tourist attraction"), HISTORICAL("Historical place"),
    SHOPPING("Shopping"), NATURE("Nature"), ADVENTURE("Adventure"), OTHER("Other")
}

enum class BookingType(val label: String) { FLIGHT("Flight"), TRAIN("Train"), BUS("Bus"), HOTEL("Hotel"), ACTIVITY("Activity"), FERRY("Ferry"), OTHER("Other") }

enum class ContactType(val label: String) {
    HOTEL_RECEPTION("Hotel reception"), HOTEL_OWNER("Hotel owner"), TAXI_DRIVER("Taxi driver"), AUTO_DRIVER("Auto driver"),
    TOUR_GUIDE("Tour guide"), TRAVEL_AGENT("Travel agent"), EMERGENCY("Emergency contact"), RESTAURANT("Restaurant"), OTHER("Other")
}

enum class Mood(val label: String, val emoji: String) {
    JOYFUL("Joyful", "😄"), CALM("Calm", "😌"), ADVENTUROUS("Adventurous", "🤩"), GRATEFUL("Grateful", "🥰"),
    TIRED("Tired", "😴"), REFLECTIVE("Reflective", "🤔"), AMAZED("Amazed", "😮"), MIXED("Mixed", "😐")
}

/** What a [TripPhotoEntity] belongs to. Gallery photos have [GENERAL]. */
enum class PhotoOwner(val folder: String) {
    GENERAL("gallery"), DIARY("diary"), HOTEL("hotels"), PLACE("places"), ACTIVITY("activities")
}

/** What a [DocumentEntity] belongs to. */
enum class DocumentOwner { BOOKING, TRIP }
