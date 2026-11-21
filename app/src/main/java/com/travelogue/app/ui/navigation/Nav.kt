package com.travelogue.app.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Navigation actions available to every screen. Screens never touch the NavController directly,
 * which keeps routes in one place.
 */
@Stable
class Nav(val controller: NavHostController) {
    fun back() {
        if (controller.previousBackStackEntry != null) controller.popBackStack()
    }

    private fun go(route: Any) = controller.navigate(route) { launchSingleTop = true }

    /** Switches bottom-bar tab, preserving each tab's state. */
    fun tab(route: Any) = controller.navigate(route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun newTrip() = go(TripEditorRoute())
    fun editTrip(tripId: Long) = go(TripEditorRoute(tripId))
    fun trip(tripId: Long, tab: Int = TripTab.OVERVIEW) = go(TripDetailRoute(tripId, tab))
    fun tripFinance(tripId: Long) = go(TripFinanceRoute(tripId))
    fun tripTimeline(tripId: Long) = go(TripTimelineRoute(tripId))
    fun tripGallery(tripId: Long) = go(TripGalleryRoute(tripId))

    /** After creating a trip, replace the editor with the new trip's detail screen. */
    fun replaceWithTrip(tripId: Long) = controller.navigate(TripDetailRoute(tripId)) {
        popUpTo<TripEditorRoute> { inclusive = true }
    }

    fun expenseEditor(tripId: Long, expenseId: Long = 0) = go(ExpenseEditorRoute(tripId, expenseId))
    fun activityEditor(tripId: Long, activityId: Long = 0, dayId: Long = 0) = go(ActivityEditorRoute(tripId, activityId, dayId))
    fun hotelEditor(tripId: Long, hotelId: Long = 0) = go(HotelEditorRoute(tripId, hotelId))
    fun hotel(hotelId: Long) = go(HotelDetailRoute(hotelId))
    fun placeEditor(tripId: Long, placeId: Long = 0) = go(PlaceEditorRoute(tripId, placeId))
    fun place(placeId: Long) = go(PlaceDetailRoute(placeId))
    fun bookingEditor(tripId: Long, bookingId: Long = 0) = go(BookingEditorRoute(tripId, bookingId))
    fun contactEditor(tripId: Long, contactId: Long = 0) = go(ContactEditorRoute(tripId, contactId))
    fun diaryEditor(tripId: Long, entryId: Long = 0) = go(DiaryEditorRoute(tripId, entryId))
    fun diaryEntry(entryId: Long) = go(DiaryDetailRoute(entryId))
    fun photo(tripId: Long, photoId: Long, favouritesOnly: Boolean = false) = go(PhotoViewerRoute(tripId, photoId, favouritesOnly))

    fun categories() = go(ManageCategoriesRoute)
    fun travellers() = go(ManageTravellersRoute)
}
