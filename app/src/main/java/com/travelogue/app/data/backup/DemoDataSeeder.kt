package com.travelogue.app.data.backup

import android.content.Context
import com.travelogue.app.core.storage.MediaStorage
import com.travelogue.app.data.db.AppDatabase
import com.travelogue.app.data.db.entity.*
import com.travelogue.app.data.repository.*
import com.travelogue.app.domain.SplitCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.random.Random

/**
 * Optional demo content (Settings → Load sample trips). Everything is created through the normal
 * repositories, with real photos of each place bundled in the app (see [SamplePhotos]). Demo trips have a "demo-" uuid prefix
 * so they can be removed without touching the user's own trips.
 */
class DemoDataSeeder(
    private val context: Context,
    private val db: AppDatabase,
    private val media: MediaStorage,
    private val trips: TripRepository,
    private val expenses: ExpenseRepository,
    private val journal: JournalRepository,
    private val diary: DiaryRepository,
    private val photos: PhotoRepository,
    private val settings: com.travelogue.app.data.settings.SettingsRepository,
) {
    private val today = LocalDate.now()

    suspend fun remove() = withContext(Dispatchers.IO) {
        trips.observeTripsOnce().filter { it.uuid.startsWith(PREFIX) }.forEach { trips.delete(it.id) }
        // Take the sample travellers with them, but only the ones seeding itself created, and only
        // if nothing of the user's refers to them now (deleteTraveller refuses otherwise).
        settings.demoTravellerIds().forEach { trips.deleteTraveller(it) }
        settings.setDemoTravellerIds(emptyList())
    }

    suspend fun seed() = withContext(Dispatchers.IO) {
        expenses.ensureDefaultCategories()
        remove()
        val before = db.travellerDao().getAll().map { it.id }.toSet()
        try {
            kerala()
            portugal()
            kyoto()
        } finally {
            settings.setDemoTravellerIds(db.travellerDao().getAll().map { it.id }.filter { it !in before })
        }
    }

    // ------------------------------------------------------------------------------------------

    private suspend fun kerala() {
        val start = today.minusDays(128)
        val end = start.plusDays(5)
        val uuid = PREFIX + UUID.randomUUID()
        val cover = cover(uuid, "k_cover")
        val tripId = trips.save(
            TripEntity(
                uuid = uuid, name = "Kerala Backwaters", destination = "Kochi & Alleppey", country = "India",
                startDate = start, endDate = end, travellerCount = 3, coverPath = cover, status = TripStatus.COMPLETED,
                budget = 90000.0, currency = "INR", travelType = TravelType.FRIENDS,
                description = "Six slow days of coconut groves, houseboats and far too much appam. Our first trip together since college.",
                notes = "Carry mosquito repellent. Book the houseboat directly, not via the agent at the jetty.",
            ),
            listOf("Asha", "Dev", "Nikhil"),
        )
        val t = trips.getTravellers(tripId).associateByTo(java.util.TreeMap(String.CASE_INSENSITIVE_ORDER)) { it.name }
        val ids = t.values.map { it.id }
        val cat = categories()

        suspend fun exp(title: String, amount: Double, c: String, day: Int, hour: Int, payer: String, loc: String = "", split: Boolean = true, method: PaymentMethod = PaymentMethod.UPI) {
            val e = ExpenseEntity(
                tripId = tripId, title = title, amount = amount, currency = "INR", categoryId = cat[c], date = start.plusDays(day.toLong()),
                time = LocalTime.of(hour, (amount.toInt() * 7) % 60), location = loc, paidByTravellerId = t[payer]?.id, paymentMethod = method,
                splitType = if (split) SplitType.EQUAL else SplitType.NONE,
            )
            expenses.save(e, if (split) SplitCalculator.equalShares(amount, ids) else emptyMap())
        }
        exp("Flights Bengaluru → Kochi", 14850.0, "transport", 0, 7, "Asha", "BLR Airport", method = PaymentMethod.CARD)
        exp("Airport taxi to Fort Kochi", 1450.0, "transport", 0, 11, "Dev", "Kochi")
        exp("Kashi Art Café brunch", 1860.0, "restaurant", 0, 13, "Nikhil", "Fort Kochi")
        exp("Kathakali performance", 1500.0, "ticket", 0, 17, "Asha", "Kerala Kathakali Centre")
        exp("Seafood dinner at the fish market", 2640.0, "food", 0, 20, "Dev", "Fort Kochi")
        exp("Spice market shopping", 2200.0, "shopping", 1, 10, "Asha", "Mattancherry", split = false, method = PaymentMethod.CASH)
        exp("Houseboat — 1 night, all meals", 16500.0, "hotel", 2, 12, "Nikhil", "Alleppey", method = PaymentMethod.BANK_TRANSFER)
        exp("Toddy shop snacks", 780.0, "food", 2, 18, "Dev", "Kuttanad", method = PaymentMethod.CASH)
        exp("Fuel for the drive to Munnar", 3200.0, "fuel", 3, 9, "Dev", "NH85")
        exp("Tea museum tickets", 375.0, "ticket", 3, 15, "Asha", "Munnar")
        exp("Homestay — 2 nights", 9600.0, "hotel", 3, 16, "Asha", "Munnar", method = PaymentMethod.CARD)
        exp("Parking at Eravikulam", 120.0, "parking", 4, 8, "Nikhil", "Eravikulam", method = PaymentMethod.CASH)
        exp("Jeep safari", 3000.0, "activity", 4, 9, "Nikhil", "Kolukkumalai")
        exp("Tea & cardamom for home", 1650.0, "shopping", 4, 14, "Dev", "Munnar", split = false, method = PaymentMethod.CASH)
        exp("Farewell dinner", 3420.0, "restaurant", 5, 20, "Asha", "Kochi")
        exp("Train Ernakulam → Bengaluru", 4185.0, "transport", 5, 22, "Nikhil", "Ernakulam Jn", method = PaymentMethod.CARD)
        expenses.addSettlement(SettlementEntity(tripId = tripId, fromTravellerId = t.getValue("Dev").id, toTravellerId = t.getValue("Asha").id, amount = 3000.0, date = end, note = "UPI on the train"))

        val houseboat = journal.saveHotel(
            HotelEntity(
                tripId = tripId, name = "Lakes & Lagoons Houseboat", address = "Punnamada Jetty, Alappuzha, Kerala",
                checkIn = start.plusDays(2), checkOut = start.plusDays(3), roomType = "2-bedroom premium houseboat",
                bookingReference = "LL-58213", bookingPlatform = "Direct", phone = "+91 94470 00000", pricePerNight = 16500.0, totalPrice = 16500.0,
                currency = "INR", paymentStatus = PaymentStatus.PAID, amenities = "AC bedrooms, All meals, Sun deck, Fishing rods",
                review = HotelReview(5, 4, 5, 5, 4, "Waking up to mist on the lake with chai on the deck was the highlight of the trip. The cook, Babu chettan, made the best karimeen pollichathu we've ever had.", "Incredible food\nSilent, slow cruise\nFriendly crew", "AC only runs at night\nLimited Wi-Fi", true),
            ),
        )
        addPhotos(tripId, uuid, PhotoOwner.HOTEL, houseboat, start.plusDays(2).atTime(15, 0), "k_hb1" to "Our houseboat at Punnamada", "k_hb2" to "Breakfast on the deck")
        val homestay = journal.saveHotel(
            HotelEntity(
                tripId = tripId, name = "Misty Hills Homestay", address = "Chithirapuram, Munnar", checkIn = start.plusDays(3), checkOut = end,
                roomType = "Family room with valley view", bookingReference = "MH2291", bookingPlatform = "Phone booking", phone = "+91 98950 00000",
                pricePerNight = 4800.0, totalPrice = 9600.0, currency = "INR", paymentStatus = PaymentStatus.PAID, amenities = "Breakfast, Parking, Hot water, Garden",
                review = HotelReview(4, 4, 5, 5, 5, "A family home tucked into the tea slopes. Simple rooms, huge hospitality.", "Homely breakfast\nGreat hosts", "Steep road up\nCold nights", true),
            ),
        )
        addPhotos(tripId, uuid, PhotoOwner.HOTEL, homestay, start.plusDays(3).atTime(16, 30), "k_homestay" to "Valley view from the room")

        val days = journal.getDays(tripId)
        suspend fun act(d: Int, name: String, loc: String, h1: Int, h2: Int, est: Double?, actual: Double?, desc: String = "", status: ActivityStatus = ActivityStatus.COMPLETED) {
            journal.saveActivity(
                ItineraryActivityEntity(tripId = tripId, dayId = days[d].id, name = name, location = loc, startTime = LocalTime.of(h1, 0), endTime = LocalTime.of(h2, 0), description = desc, estimatedCost = est, actualCost = actual, status = status),
            )
        }
        journal.updateDay(days[0].copy(title = "Arrival in Fort Kochi"))
        act(0, "Chinese fishing nets at sunset", "Fort Kochi beach", 17, 18, null, null, "Watch the nets being lowered as the sun goes down.")
        act(0, "Kathakali performance", "Kerala Kathakali Centre", 18, 20, 1500.0, 1500.0, "Arrive early to watch the make-up ritual.")
        journal.updateDay(days[1].copy(title = "Mattancherry wanderings"))
        act(1, "Jew Town & spice market", "Mattancherry", 10, 13, 1000.0, 2200.0)
        act(1, "Mattancherry Palace murals", "Mattancherry", 14, 15, 50.0, null, status = ActivityStatus.SKIPPED)
        journal.updateDay(days[2].copy(title = "Houseboat day"))
        act(2, "Board the houseboat", "Punnamada jetty", 12, 13, null, null)
        act(2, "Village canoe ride", "Kuttanad", 16, 18, 800.0, 700.0, "Narrow canals the big boats can't reach.")
        journal.updateDay(days[3].copy(title = "Up into the hills"))
        act(3, "Drive to Munnar", "NH85", 8, 13, 3000.0, 3200.0)
        act(3, "Tea Museum", "Nallathanni", 15, 16, 375.0, 375.0)
        journal.updateDay(days[4].copy(title = "Sunrise at Kolukkumalai"))
        act(4, "Jeep safari to Kolukkumalai", "Kolukkumalai", 4, 9, 3000.0, 3000.0, "The world's highest tea estate — leave at 4am for sunrise.")
        act(4, "Eravikulam National Park", "Rajamalai", 11, 14, 600.0, null, status = ActivityStatus.SKIPPED)

        val fortKochi = journal.savePlace(PlaceEntity(tripId = tripId, name = "Fort Kochi Beach", address = "Fort Kochi, Kochi", mapsUrl = "https://maps.google.com/?q=Fort+Kochi+Beach", latitude = 9.9658, longitude = 76.2421, category = PlaceCategory.BEACH, visitDate = start, rating = 4, description = "Chinese fishing nets, old Dutch bungalows and the best evening light."))
        addPhotos(tripId, uuid, PhotoOwner.PLACE, fortKochi, start.atTime(18, 0), "k_fk1" to "Fishing nets at dusk", "k_fk2" to "")
        journal.savePlace(PlaceEntity(tripId = tripId, name = "Kashi Art Café", address = "Burgher Street, Fort Kochi", category = PlaceCategory.RESTAURANT, visitDate = start, rating = 5, description = "Gallery-café with a leafy courtyard. Get the chocolate cake."))
        val vembanad = journal.savePlace(PlaceEntity(tripId = tripId, name = "Vembanad Lake", address = "Alappuzha", latitude = 9.5916, longitude = 76.3940, category = PlaceCategory.NATURE, visitDate = start.plusDays(2), rating = 5, description = "Kerala's longest lake. Kingfishers everywhere."))
        addPhotos(tripId, uuid, PhotoOwner.PLACE, vembanad, start.plusDays(2).atTime(7, 0), "k_vemb" to "Morning mist on Vembanad")
        val kolukkumalai = journal.savePlace(PlaceEntity(tripId = tripId, name = "Kolukkumalai Tea Estate", address = "Suryanelli, Munnar", category = PlaceCategory.ADVENTURE, visitDate = start.plusDays(4), rating = 5, description = "Bumpy jeep ride, unforgettable sunrise above the clouds."))
        addPhotos(tripId, uuid, PhotoOwner.PLACE, kolukkumalai, start.plusDays(4).atTime(6, 15), "k_kol1" to "Sunrise above the clouds", "k_kol2" to "Tea rows")
        journal.savePlace(PlaceEntity(tripId = tripId, name = "Mattancherry Palace", address = "Mattancherry, Kochi", category = PlaceCategory.HISTORICAL, visitDate = start.plusDays(1), rating = 0, description = "Closed on the day we went — next time."))

        journal.saveBooking(BookingEntity(tripId = tripId, title = "BLR → COK", type = BookingType.FLIGHT, provider = "IndiGo 6E 467", reference = "QX7P2L", fromPlace = "Bengaluru", toPlace = "Kochi", departure = start.atTime(7, 45), arrival = start.atTime(9, 0), amount = 14850.0, currency = "INR"))
        journal.saveBooking(BookingEntity(tripId = tripId, title = "Ernakulam → Bengaluru", type = BookingType.TRAIN, provider = "Kochuveli Express · 3A", reference = "PNR 4521890336", fromPlace = "Ernakulam", toPlace = "Bengaluru", departure = end.atTime(22, 10), arrival = end.plusDays(1).atTime(9, 30), amount = 4185.0, currency = "INR"))

        journal.saveContact(TravelContactEntity(tripId = tripId, name = "Babu (houseboat cook)", type = ContactType.HOTEL_OWNER, phone = "+91 94470 00000", whatsapp = "+91 94470 00000", associatedWith = "Lakes & Lagoons Houseboat"))
        journal.saveContact(TravelContactEntity(tripId = tripId, name = "Shibu", type = ContactType.TAXI_DRIVER, phone = "+91 98460 00000", whatsapp = "+91 98460 00000", description = "Honest, knows every shortcut in Kochi."))
        journal.saveContact(TravelContactEntity(tripId = tripId, name = "Misty Hills reception", type = ContactType.HOTEL_RECEPTION, phone = "+91 98950 00000", associatedWith = "Misty Hills Homestay"))
        journal.saveContact(TravelContactEntity(tripId = tripId, name = "Tourist police Kochi", type = ContactType.EMERGENCY, phone = "+91 484 2668 000"))

        val e1 = diary.save(DiaryEntryEntity(tripId = tripId, title = "Nets, light and Kathakali", body = "We landed into thick, warm air that smelled of rain and frying banana chips. By evening we were on Fort Kochi beach watching the giant Chinese nets dip into the water, four men leaning on the counterweights in perfect rhythm.\n\nThe Kathakali performance afterwards was hypnotic — an hour of make-up, then eyes that could tell a whole story without a word. Dev tried to copy the eye movements all the way to dinner.", dateTime = start.atTime(22, 30), location = "Fort Kochi", mood = Mood.AMAZED), listOf("culture", "sunset", "food"))
        addPhotos(tripId, uuid, PhotoOwner.DIARY, e1, start.atTime(18, 10), "k_e1" to "The first sunset of the trip")
        val e2 = diary.save(DiaryEntryEntity(tripId = tripId, title = "A day that moved at the speed of water", body = "No signal, no plans. The houseboat drifted past paddy fields lower than the water, ducks herded by a single man with a stick, kids waving from every jetty. Lunch was served on banana leaves on the deck.\n\nAt night the crew moored by a village and we fell asleep to frogs and distant temple music.", dateTime = start.plusDays(2).atTime(21, 0), location = "Vembanad Lake", mood = Mood.CALM), listOf("houseboat", "slow travel"))
        addPhotos(tripId, uuid, PhotoOwner.DIARY, e2, start.plusDays(2).atTime(16, 0), "k_e2a" to "Paddy fields below the water line", "k_e2b" to "Lunch on the deck", "k_e2c" to "")
        val e3 = diary.save(DiaryEntryEntity(tripId = tripId, title = "Above the clouds", body = "4am alarm, a jeep that rattled every bone, and then — the sun came up over a sea of cloud with the tea rows glowing gold. Nobody spoke for a full five minutes.", dateTime = start.plusDays(4).atTime(10, 0), location = "Kolukkumalai", mood = Mood.GRATEFUL), listOf("sunrise", "hills"))
        addPhotos(tripId, uuid, PhotoOwner.DIARY, e3, start.plusDays(4).atTime(6, 20), "k_e3a" to "Worth the 4am alarm", "k_e3b" to "")
        addPhotos(tripId, uuid, PhotoOwner.GENERAL, null, start.plusDays(1).atTime(11, 0), "k_g1" to "Spice market colours", "k_g2" to "Mattancherry streets")
        favouriteFirst(tripId, 3)
    }

    private suspend fun portugal() {
        val start = today.minusDays(46)
        val end = start.plusDays(6)
        val uuid = PREFIX + UUID.randomUUID()
        val cover = cover(uuid, "p_cover")
        val tripId = trips.save(
            TripEntity(
                uuid = uuid, name = "Lisbon & Porto", destination = "Lisbon, Sintra, Porto", country = "Portugal",
                startDate = start, endDate = end, travellerCount = 2, coverPath = cover, status = TripStatus.COMPLETED,
                budget = 220000.0, currency = "INR", travelType = TravelType.COUPLE,
                description = "Tiled façades, trams that climb impossible hills, and custard tarts at every corner.",
            ),
            listOf("Asha", "Rohan"),
        )
        trips.setRate(tripId, "EUR", 92.5)
        val t = trips.getTravellers(tripId).associateByTo(java.util.TreeMap(String.CASE_INSENSITIVE_ORDER)) { it.name }
        val ids = t.values.map { it.id }
        val cat = categories()
        suspend fun exp(title: String, amount: Double, currency: String, c: String, day: Int, payer: String, loc: String, split: SplitType = SplitType.EQUAL) {
            val shares = when (split) {
                SplitType.EQUAL -> SplitCalculator.equalShares(amount, ids)
                SplitType.CUSTOM -> mapOf(t.getValue("Asha").id to amount * 0.7, t.getValue("Rohan").id to amount * 0.3)
                SplitType.NONE -> emptyMap()
            }
            expenses.save(
                ExpenseEntity(tripId = tripId, title = title, amount = amount, currency = currency, categoryId = cat[c], date = start.plusDays(day.toLong()), time = LocalTime.of(10 + day, 30), location = loc, paidByTravellerId = t[payer]?.id, paymentMethod = PaymentMethod.CARD, splitType = split),
                shares,
            )
        }
        exp("Return flights BLR ⇄ LIS", 96400.0, "INR", "transport", 0, "Rohan", "Bengaluru")
        exp("Alfama apartment — 3 nights", 465.0, "EUR", "hotel", 0, "Asha", "Lisbon")
        exp("Pastéis de Belém", 12.6, "EUR", "food", 1, "Rohan", "Belém")
        exp("Tram 28 day passes", 13.2, "EUR", "transport", 1, "Asha", "Lisbon")
        exp("Pena Palace tickets", 40.0, "EUR", "ticket", 2, "Rohan", "Sintra")
        exp("Seafood at Cervejaria Ramiro", 88.0, "EUR", "restaurant", 2, "Asha", "Lisbon")
        exp("Train Lisbon → Porto", 49.6, "EUR", "transport", 3, "Rohan", "Santa Apolónia")
        exp("Ribeira guesthouse — 3 nights", 390.0, "EUR", "hotel", 3, "Rohan", "Porto")
        exp("Port wine cellar tour", 36.0, "EUR", "activity", 4, "Asha", "Vila Nova de Gaia")
        exp("Azulejo tiles & ceramics", 74.0, "EUR", "shopping", 5, "Asha", "Porto", SplitType.CUSTOM)
        exp("Francesinha dinner", 31.5, "EUR", "restaurant", 5, "Rohan", "Porto")

        val apt = journal.saveHotel(HotelEntity(tripId = tripId, name = "Casa do Alfama", address = "Rua de São Miguel, Lisbon", checkIn = start, checkOut = start.plusDays(3), roomType = "One-bedroom apartment", bookingReference = "HMX4Q2", bookingPlatform = "Booking site", pricePerNight = 155.0, totalPrice = 465.0, currency = "EUR", amenities = "Wi-Fi, Kitchen, Balcony, AC", review = HotelReview(4, 5, 5, 4, 4, "Tiny balcony over the rooftops of Alfama. Fado drifted up from the street every night.", "Location\nSpotless", "Steep stairs, no lift", true)))
        addPhotos(tripId, uuid, PhotoOwner.HOTEL, apt, start.atTime(15, 0), "p_apt" to "Rooftops from the balcony")
        journal.saveHotel(HotelEntity(tripId = tripId, name = "Ribeira Riverside Guesthouse", address = "Cais da Ribeira, Porto", checkIn = start.plusDays(3), checkOut = end, roomType = "Double room, river view", pricePerNight = 130.0, totalPrice = 390.0, currency = "EUR", amenities = "Breakfast, Wi-Fi", review = HotelReview(3, 4, 5, 3, 3, "Unbeatable view of the Dom Luís bridge, but thin walls.", "View\nBreakfast pastries", "Noisy at night", false)))

        val belem = journal.savePlace(PlaceEntity(tripId = tripId, name = "Belém Tower", category = PlaceCategory.HISTORICAL, visitDate = start.plusDays(1), rating = 4, latitude = 38.6916, longitude = -9.2160, description = "Manueline lace in stone at the mouth of the Tagus."))
        addPhotos(tripId, uuid, PhotoOwner.PLACE, belem, start.plusDays(1).atTime(12, 0), "p_belem" to "")
        val pena = journal.savePlace(PlaceEntity(tripId = tripId, name = "Pena Palace", address = "Sintra", category = PlaceCategory.ATTRACTION, visitDate = start.plusDays(2), rating = 5, description = "A fairytale painted in yellow and red, floating in fog."))
        addPhotos(tripId, uuid, PhotoOwner.PLACE, pena, start.plusDays(2).atTime(11, 0), "p_pena1" to "Pena in the fog", "p_pena2" to "")
        journal.savePlace(PlaceEntity(tripId = tripId, name = "Livraria Lello", address = "Porto", category = PlaceCategory.SHOPPING, visitDate = start.plusDays(4), rating = 3, description = "Beautiful staircase, very long queue."))
        journal.saveBooking(BookingEntity(tripId = tripId, title = "Lisbon → Porto", type = BookingType.TRAIN, provider = "Alfa Pendular", reference = "CP 7Q19X", fromPlace = "Lisbon", toPlace = "Porto", departure = start.plusDays(3).atTime(9, 39), arrival = start.plusDays(3).atTime(12, 28), amount = 49.6, currency = "EUR"))
        val d = diary.save(DiaryEntryEntity(tripId = tripId, title = "Lost in Alfama", body = "We put the map away and followed the sound of fado down staircases that turned into alleys that turned into somebody's courtyard. Ended up with grilled sardines and a jug of vinho verde on a plastic table, perfect.", dateTime = start.plusDays(1).atTime(23, 0), location = "Alfama, Lisbon", mood = Mood.JOYFUL), listOf("food", "music"))
        addPhotos(tripId, uuid, PhotoOwner.DIARY, d, start.plusDays(1).atTime(20, 0), "p_d1" to "Alfama at night", "p_d2" to "Sardines!")
        favouriteFirst(tripId, 2)
    }

    private suspend fun kyoto() {
        val start = today.plusDays(24)
        val end = start.plusDays(7)
        val uuid = PREFIX + UUID.randomUUID()
        val cover = cover(uuid, "y_cover")
        val tripId = trips.save(
            TripEntity(
                uuid = uuid, name = "Kyoto in Winter", destination = "Kyoto & Nara", country = "Japan",
                startDate = start, endDate = end, travellerCount = 1, coverPath = cover, status = TripStatus.UPCOMING,
                budget = 260000.0, currency = "INR", travelType = TravelType.SOLO,
                description = "Temples in the snow, quiet onsen evenings and as many bowls of ramen as possible.",
                notes = "Get a Suica card at the airport. Temples close early in winter (~4:30pm).",
            ),
            listOf("Asha"),
        )
        trips.setRate(tripId, "JPY", 0.56)
        val cat = categories()
        val me = trips.getTravellers(tripId).first().id
        expenses.save(ExpenseEntity(tripId = tripId, title = "Flights BLR → KIX", amount = 68200.0, currency = "INR", categoryId = cat["transport"], date = today.minusDays(20), paidByTravellerId = me, paymentMethod = PaymentMethod.CARD), emptyMap())
        expenses.save(ExpenseEntity(tripId = tripId, title = "Ryokan deposit", amount = 30000.0, currency = "JPY", categoryId = cat["hotel"], date = today.minusDays(12), paidByTravellerId = me, paymentMethod = PaymentMethod.CARD), emptyMap())
        journal.saveHotel(HotelEntity(tripId = tripId, name = "Gion Hatanaka Ryokan", address = "Gion, Higashiyama, Kyoto", checkIn = start, checkOut = start.plusDays(4), roomType = "Japanese-style room with garden view", bookingReference = "GH-7731", pricePerNight = 38000.0, totalPrice = 152000.0, currency = "JPY", paymentStatus = PaymentStatus.PARTIALLY_PAID, amenities = "Onsen, Breakfast, Kaiseki dinner"))
        journal.saveBooking(BookingEntity(tripId = tripId, title = "BLR → KIX", type = BookingType.FLIGHT, provider = "via Singapore", reference = "8KJ3TZ", fromPlace = "Bengaluru", toPlace = "Osaka Kansai", departure = start.minusDays(1).atTime(23, 50), arrival = start.atTime(13, 5), amount = 68200.0, currency = "INR"))
        journal.saveBooking(BookingEntity(tripId = tripId, title = "Haruka Express", type = BookingType.TRAIN, provider = "JR West", fromPlace = "Kansai Airport", toPlace = "Kyoto", departure = start.atTime(14, 16), arrival = start.atTime(15, 35), amount = 3640.0, currency = "JPY", paymentStatus = PaymentStatus.PENDING))
        val days = journal.getDays(tripId)
        suspend fun plan(d: Int, name: String, loc: String, h1: Int, h2: Int, est: Double?) =
            journal.saveActivity(ItineraryActivityEntity(tripId = tripId, dayId = days[d].id, name = name, location = loc, startTime = LocalTime.of(h1, 0), endTime = LocalTime.of(h2, 0), estimatedCost = est))
        journal.updateDay(days[0].copy(title = "Arrive & settle in"))
        plan(0, "Check in at the ryokan", "Gion", 16, 17, null)
        plan(0, "Evening walk through Gion", "Hanamikoji", 18, 20, null)
        journal.updateDay(days[1].copy(title = "Eastern temples"))
        plan(1, "Kiyomizu-dera at opening", "Higashiyama", 6, 8, 400.0)
        plan(1, "Sannenzaka & Ninenzaka", "Higashiyama", 8, 10, null)
        plan(1, "Fushimi Inari at dusk", "Fushimi", 16, 19, null)
        journal.updateDay(days[2].copy(title = "Arashiyama"))
        plan(2, "Bamboo grove before the crowds", "Arashiyama", 7, 9, null)
        plan(2, "Tenryū-ji garden", "Arashiyama", 9, 11, 500.0)
        journal.updateDay(days[3].copy(title = "Day trip to Nara"))
        plan(3, "Tōdai-ji & the deer park", "Nara", 9, 13, 600.0)
        journal.saveContact(TravelContactEntity(tripId = tripId, name = "Ryokan front desk", type = ContactType.HOTEL_RECEPTION, phone = "+81 75 541 5315", associatedWith = "Gion Hatanaka Ryokan"))
        journal.saveContact(TravelContactEntity(tripId = tripId, name = "Japan emergency (police)", type = ContactType.EMERGENCY, phone = "110"))
        journal.savePlace(PlaceEntity(tripId = tripId, name = "Kinkaku-ji", address = "Kita Ward, Kyoto", mapsUrl = "https://maps.google.com/?q=Kinkaku-ji", category = PlaceCategory.ATTRACTION, description = "Hoping for snow on the golden pavilion."))
    }

    // ------------------------------------------------------------------------------------------

    private suspend fun categories(): Map<String, Long> = expenses.getCategories().associate { it.iconKey to it.id }

    private suspend fun favouriteFirst(tripId: Long, n: Int) {
        db.photoDao().getForTrip(tripId).shuffled(Random(tripId)).take(n).forEach { photos.setFavourite(it.id, true) }
    }

    /** Adds bundled photos ([SamplePhotos] slot to caption) to the trip, a few minutes apart from [at]. */
    private suspend fun addPhotos(tripId: Long, uuid: String, owner: PhotoOwner, ownerId: Long?, at: LocalDateTime, vararg photos: Pair<String, String>) {
        photos.forEachIndexed { i, (slot, caption) ->
            val stored = media.saveAsset(SamplePhotos.asset(slot), uuid, owner.folder)
            db.photoDao().insert(
                TripPhotoEntity(
                    tripId = tripId, ownerType = owner, ownerId = ownerId, relativePath = stored.relativePath, caption = caption,
                    takenAt = at.plusMinutes(i * 17L), width = stored.width, height = stored.height, sizeBytes = stored.sizeBytes, sortOrder = i,
                ),
            )
        }
    }

    private suspend fun cover(uuid: String, slot: String): String = media.saveAsset(SamplePhotos.asset(slot), uuid, "cover").relativePath

    companion object {
        const val PREFIX = "demo-"
    }
}

private suspend fun TripRepository.observeTripsOnce(): List<TripEntity> = observeTrips().first()
