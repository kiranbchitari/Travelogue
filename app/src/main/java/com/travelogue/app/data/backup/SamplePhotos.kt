package com.travelogue.app.data.backup

/**
 * The real photos the sample trips use. Each is bundled as `assets/sample/<slot>.webp`, resized from
 * the Wikimedia Commons original to 1440px on the long edge. Their licences (CC BY, CC BY-SA, public
 * domain) ask for the author and licence to be named, which Settings → Sample photo credits does.
 */
object SamplePhotos {
    /** [source] is the photo's Commons page, which shows the original and its full licence. */
    class Credit(val slot: String, val subject: String, val author: String, val license: String, val source: String)

    fun asset(slot: String): String = "sample/$slot.webp"

    val credits: List<Credit> = listOf(
        Credit("k_cover", "Houseboat on the Alappuzha backwaters", "Paul Arps from The Netherlands", "CC BY 2.0", "https://commons.wikimedia.org/wiki/File:Houseboat_on_Alleppey_backwaters_(Kerala,_India_2023)_(52704809623).jpg"),
        Credit("k_hb1", "Houseboat, Kerala backwaters", "Ingo Mehling", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Nedumudy_-_Houseboat.jpg"),
        Credit("k_hb2", "Breakfast on a houseboat", "P.K.Niyogi", "Public domain", "https://commons.wikimedia.org/wiki/File:Kerala_Houseboat_Breakfast.JPG"),
        Credit("k_homestay", "Valley near Munnar", "Manoj M Shenoy", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Munnar_valley_and_meadows.jpg"),
        Credit("k_fk1", "Chinese fishing nets at sunset, Fort Kochi", "Bockomet", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Fishing_nets_in_Fort_Kochi.jpg"),
        Credit("k_fk2", "Fort Kochi beach", "Vis M", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Fort_Kochi_beach_shore.jpg"),
        Credit("k_vemb", "Boatman on Vembanad Lake", "Ravikiran Rao", "CC BY-SA 3.0", "https://commons.wikimedia.org/wiki/File:Boatman_on_Vembanad_Lake.JPG"),
        Credit("k_kol1", "Sunrise from Kolukkumalai", "Yahya", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Kolukkumalai_sunrise_view_point_55.jpg"),
        Credit("k_kol2", "Tea gardens, Munnar", "Joydeep Chakraborty", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Munnar_tea_garden_Nov-2026_2.jpg"),
        Credit("k_e1", "Sunset at Fort Kochi", "Rangan Datta Wiki", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Fort_Kochi_Beach_Sunset_3.jpg"),
        Credit("k_e2a", "Paddy fields beside a canal, Kuttanad", "Sourav Niyogi", "CC BY-SA 3.0", "https://commons.wikimedia.org/wiki/File:Kerala_Kuttanad2.jpg"),
        Credit("k_e2b", "Sadya on a banana leaf", "Ranjithsiji", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Sadya-Aluva-Kerala-IMG_20210320_204506.jpg"),
        Credit("k_e2c", "Backwater canal, Kerala", "Vyacheslav Argenberg", "CC BY 4.0", "https://commons.wikimedia.org/wiki/File:Kerala_backwaters,_Canal,_Palm_trees,_India.jpg"),
        Credit("k_e3a", "Dawn from Kolukkumalai, Munnar", "Jan J George", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:The_Stary_Sunrise_View_from_Kolukkumalai,_Munnar.jpg"),
        Credit("k_e3b", "Tea bushes, Munnar", "Nikolas Becker", "CC BY-SA 3.0", "https://commons.wikimedia.org/wiki/File:Tea_Estate_Munnar.jpg"),
        Credit("k_g1", "Spice shop, India", "The U.S. Food and Drug Administration", "Public domain", "https://commons.wikimedia.org/wiki/File:Foods_and_Flavors_of_India_(8342610160).jpg"),
        Credit("k_g2", "Jew Town, Mattancherry", "Rudolph.A.furtado", "CC0", "https://commons.wikimedia.org/wiki/File:Mattancherry_Jew_Town.JPG"),
        Credit("p_cover", "Tram 28, Lisbon", "Christine Zenino from Chicago, US", "CC BY 2.0", "https://commons.wikimedia.org/wiki/File:Tram_28;_Lisbon_(5282021178).jpg"),
        Credit("p_apt", "Alfama rooftops, Lisbon", "Bernt Rostad from Oslo, Norway", "CC BY 2.0", "https://commons.wikimedia.org/wiki/File:Alfama_(3903814530).jpg"),
        Credit("p_belem", "Belém Tower, Lisbon", "Berthold Werner", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Lisbon_Torre_de_Bel%C3%A9m_BW_2018-10-03_16-35-39.jpg"),
        Credit("p_pena1", "Pena Palace in the fog, Sintra", "Andre Carvalho Photography", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Palacio_Nacional_da_Pena.jpg"),
        Credit("p_pena2", "Pena Palace, Sintra", "CEphoto, Uwe Aranas", "CC BY-SA 3.0", "https://commons.wikimedia.org/wiki/File:Sintra_Portugal_Pal%C3%A1cio_da_Pena-01.jpg"),
        Credit("p_d1", "Alfama at night, Lisbon", "TeWeBs", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Lisbon_Alfama_Night.JPG"),
        Credit("p_d2", "Grilled sardines", "Joseolgon", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Sardinhas_(2).jpg"),
        Credit("y_cover", "Kinkaku-ji in snow, Kyoto", "Hyppolyte de Saint-Rambert", "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Kyoto_Rokuon-ji_Kinkaku-ji_c14.jpg"),
    )
}
