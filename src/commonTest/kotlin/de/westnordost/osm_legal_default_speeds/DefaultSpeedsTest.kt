package de.westnordost.osm_legal_default_speeds

import de.westnordost.osm_legal_default_speeds.tagfilter.ParseException
import de.westnordost.osm_legal_default_speeds.LegalDefaultSpeeds.Result.Certitude.*
import kotlin.test.*

internal class DefaultSpeedsTest {

    private val za = LegalDefaultSpeeds(
        mapOf(
            "living street" to filters("highway=living_street"), // only exact filter
            "alley" to filters("{urban} and alley=yes"), // with placeholder
            "urban" to filters("lit=yes", "highway=residential"), // both exact and fuzzy filter
            "urban state road" to filters("{urban} and {state road}"), // filter referring to relation filter
            "rural" to filters(null, "sidewalk=no"), // only fuzzy filter
            "dual carriageway" to filters("dual_carriageway=yes"), // only fuzzy filter
            "motorway" to filters("highway=motorway"),
            "state road" to filters(null, null, "type=route and ref~ZA.*"), // only relation filter
            "rural state road" to filters("{rural} and {state road}"),
        ),
        mapOf(
            "ZA" to listOf(
                road("living street", mapOf("maxspeed" to "10")),
                road("alley", mapOf("maxspeed" to "5")),
                road("urban state road", mapOf("maxspeed" to "60")),
                road("urban", mapOf("maxspeed" to "50")),
                road(null, mapOf("maxspeed" to "100")), // default rule
                road("rural", mapOf("maxspeed" to "100")),
                road("dual carriageway", mapOf("maxspeed" to "110")),
                road("rural state road", mapOf("maxspeed" to "115")),
                road("motorway", mapOf("maxspeed" to "120")),
            )
        )
    )

    @Test fun fails_on_syntax_exception_in_filter() {
        assertFailsWith(ParseException::class) {
            LegalDefaultSpeeds(
                mapOf("urban" to filters("and and")),
                mapOf("FR" to listOf(road("urban", mapOf("maxspeed" to "50"))))
            )
        }
    }

    @Test fun no_tags_match() {
        assertNull(LegalDefaultSpeeds(
            mapOf("urban" to filters("lit=yes")),
            mapOf("SD" to listOf(road("urban", mapOf("maxspeed" to "60"))))
        ).getSpeedLimits("SD", mapOf("lit" to "no")))
    }

    @Test fun unknown_country() {
        assertNull(za.getSpeedLimits("GY", mapOf("lit" to "yes")))
    }

    @Test fun fallback_when_no_tags_match() {
        assertEquals(
            LegalDefaultSpeeds.Result(null, mapOf("maxspeed" to "100"), Fallback),
            za.getSpeedLimits("ZA", mapOf("lit" to "no"))
        )
    }

    @Test fun simple_match() {
        assertEquals(
            LegalDefaultSpeeds.Result("urban", mapOf("maxspeed" to "50"), Exact),
            za.getSpeedLimits("ZA", mapOf("lit" to "yes"))
        )
    }

    @Test fun fuzzy_match() {
        assertEquals(
            LegalDefaultSpeeds.Result("urban", mapOf("maxspeed" to "50"), Fuzzy),
            za.getSpeedLimits("ZA", mapOf("highway" to "residential"))
        )
    }

    @Test fun fallback_to_country_if_subdivision_unknown() {
        assertEquals(
            LegalDefaultSpeeds.Result("urban", mapOf("maxspeed" to "50"), Exact),
            za.getSpeedLimits("ZA-NC", mapOf("lit" to "yes"))
        )
    }

    @Test fun prefer_matches_further_down_the_list() {
        assertEquals(
            LegalDefaultSpeeds.Result("motorway", mapOf("maxspeed" to "120"), Exact),
            za.getSpeedLimits("ZA", mapOf("highway" to "motorway", "lit" to "yes", "dual_carriageway" to "yes"))
        )
        assertEquals(
            LegalDefaultSpeeds.Result("dual carriageway", mapOf("maxspeed" to "110"), Exact),
            za.getSpeedLimits("ZA", mapOf("lit" to "yes", "dual_carriageway" to "yes"))
        )
    }

    @Test fun prefer_matches_further_at_the_top_of_the_list_otherwise() {
        assertEquals(
            LegalDefaultSpeeds.Result("living street", mapOf("maxspeed" to "10"), Exact),
            za.getSpeedLimits("ZA", mapOf("highway" to "living_street", "lit" to "yes"))
        )
    }

    @Test fun prefer_exact_over_fuzzy_rules() {
        assertEquals(
            LegalDefaultSpeeds.Result("urban", mapOf("maxspeed" to "50"), Exact),
            za.getSpeedLimits("ZA", mapOf("lit" to "yes", "sidewalk" to "no"))
        )
    }

    @Test fun exact_match_with_placeholder() {
        assertEquals(
            LegalDefaultSpeeds.Result("alley", mapOf("maxspeed" to "5"), Exact),
            za.getSpeedLimits("ZA", mapOf("lit" to "yes", "alley" to "yes"))
        )
    }

    @Test fun fuzzy_match_with_placeholder() {
        assertEquals(
            LegalDefaultSpeeds.Result("alley", mapOf("maxspeed" to "5"), Fuzzy),
            za.getSpeedLimits("ZA", mapOf("highway" to "residential", "alley" to "yes"))
        )
    }

    @Test fun find_contained_in_relation_with_additional_fuzzy_rule() {
        assertEquals(
            LegalDefaultSpeeds.Result("rural state road", mapOf("maxspeed" to "115"), Fuzzy),
            za.getSpeedLimits(
                "ZA",
                mapOf("sidewalk" to "no"),
                listOf(
                    mapOf("type" to "route", "ref" to "Bus 1234"),
                    mapOf("type" to "route", "ref" to "ZA 2")
                )
            )
        )
    }

    @Test fun find_contained_in_relation_with_additional_exact_rule() {
        assertEquals(
            LegalDefaultSpeeds.Result("urban state road", mapOf("maxspeed" to "60"), Exact),
            za.getSpeedLimits(
                "ZA",
                mapOf("lit" to "yes"),
                listOf(mapOf("type" to "route", "ref" to "ZA 2"))
            )
        )
    }

    @Test fun replacing_urban() {
        assertEquals(
            LegalDefaultSpeeds.Result("urban", mapOf("maxspeed" to "50"), Exact),
            za.getSpeedLimits("ZA", mapOf()) { name, ev ->
                if (name == "urban") true else ev()
            }
        )
    }

    @Test fun replacing_urban_combined_with_other_filter() {
        assertEquals(
            LegalDefaultSpeeds.Result("alley", mapOf("maxspeed" to "5"), Exact),
            za.getSpeedLimits("ZA", mapOf("alley" to "yes")) { name, ev ->
                if (name == "urban") true else ev()
            }
        )
    }

    @Test fun replacing_urban_combined_with_fuzzy_filter() {
        assertEquals(
            LegalDefaultSpeeds.Result("urban state road", mapOf("maxspeed" to "60"), Fuzzy),
            za.getSpeedLimits("ZA", mapOf("highway" to "residential")) { name, ev ->
                if (name == "state road") true else ev()
            }
        )
    }

    @Test fun removes_subtags_with_higher_speeds() {
        assertEquals(
            mapOf("maxspeed" to "60", "maxspeed:mofa" to "50"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "60",
                    "maxspeed:hgv" to "80", // this
                    "maxspeed:mofa" to "50" // but not this
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_subtags_with_higher_mph_speeds() {
        assertEquals(
            mapOf("maxspeed" to "35 mph", "maxspeed:mofa" to "10 mph"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "35 mph",
                    "maxspeed:hgv" to "40 mph", // this
                    "maxspeed:mofa" to "10 mph" // but not this
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_conditionals_with_higher_speeds() {
        assertEquals(
            mapOf("maxspeed" to "60", "maxspeed:conditional" to "50 @ (something else)"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "60",
                    "maxspeed:conditional" to "80 @ (something); 50 @ (something else)" // first, not second
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_conditionals_with_higher_mph_speeds() {
        assertEquals(
            mapOf("maxspeed" to "35 mph", "maxspeed:conditional" to "20 mph @ (something else)"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "35 mph",
                    "maxspeed:conditional" to "35 mph @ (something); 20 mph @ (something else)" // first, not second
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_all_conditionals_if_they_all_have_higher_speeds() {
        assertEquals(
            mapOf("maxspeed" to "60"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "60",
                    "maxspeed:conditional" to "80 @ (something); 60 @ (something else)"
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_all_conditionals_if_they_all_have_higher_mph_speeds() {
        assertEquals(
            mapOf("maxspeed" to "20 mph"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "20 mph",
                    "maxspeed:conditional" to "40 mph @ (something); 30 mph @ (something else)"
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_conditionals_of_subtags_with_higher_speeds() {
        assertEquals(
            mapOf("maxspeed:hgv" to "60", "maxspeed:hgv:conditional" to "50 @ (something else)"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed:hgv" to "60",
                    "maxspeed:hgv:conditional" to "80 @ (something); 50 @ (something else)"
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_conditionals_of_subtags_with_higher_mph_speeds() {
        assertEquals(
            mapOf("maxspeed:hgv" to "30 mph", "maxspeed:hgv:conditional" to "20 mph @ (something else)"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed:hgv" to "30 mph",
                    "maxspeed:hgv:conditional" to "40 mph @ (something); 20 mph @ (something else)"
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_all_conditionals_of_subtags_if_they_all_have_with_higher_speeds() {
        assertEquals(
            mapOf("maxspeed:hgv" to "60"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed:hgv" to "60",
                    "maxspeed:hgv:conditional" to "80 @ (something); 60 @ (something else)"
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_all_conditionals_of_subtags_if_they_all_have_with_higher_mph_speeds() {
        assertEquals(
            mapOf("maxspeed:hgv" to "10 mph"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed:hgv" to "10 mph",
                    "maxspeed:hgv:conditional" to "40 mph @ (something); 30 mph @ (something else)"
                ))))
            ).getSpeedLimits("AB", mapOf())!!.tags
        )
    }

    @Test fun removes_subtags_with_higher_speeds_when_lower_speed_is_specified() {
        assertEquals(
            mapOf("maxspeed:mofa" to "50"),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "100",
                    "maxspeed:hgv" to "80",
                    "maxspeed:mofa" to "50"
                ))))
            ).getSpeedLimits("AB", mapOf("maxspeed" to "80"))!!.tags
        )
        assertEquals(
            mapOf(),
            LegalDefaultSpeeds(
                mapOf(),
                mapOf("AB" to listOf(road(tags = mapOf(
                    "maxspeed" to "100",
                    "maxspeed:hgv" to "80"
                ))))
            ).getSpeedLimits("AB", mapOf("maxspeed" to "80", "maxspeed:hgv" to "50"))!!.tags
        )
    }
}

internal data class RoadTypeFilterImpl(
    override val filter: String?,
    override val fuzzyFilter: String?,
    override val relationFilter: String?
) : RoadTypeFilter

internal data class RoadTypeImpl(
    override val name: String?,
    override val tags: Map<String, String>
) : RoadType

internal fun road(name: String? = null, tags: Map<String, String> = mapOf()) =
    RoadTypeImpl(name, tags)

internal fun filters(filter: String? = null, fuzzyFilter: String? = null, relationFilter: String? = null) =
    RoadTypeFilterImpl(filter, fuzzyFilter, relationFilter)
