package app.toque.core

import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.SearchIndex
import app.toque.core.recipes.Step
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexFileTest {
    private val recipes = listOf(
        Recipe(id = "a", title = "Soupe de potiron", category = "Soupes", tags = listOf("automne"),
            ingredients = listOf(IngredientGroup("", listOf(Ingredient("500", "g", "potiron"), Ingredient("1", "", "oignon")))),
            steps = listOf(Step("Mixer le potiron")), rating = 4.5, ratingCount = 12),
        Recipe(id = "b", title = "Pâte à crêpes", category = "Desserts", favorite = true,
            ingredients = listOf(IngredientGroup("", listOf(Ingredient("250", "g", "farine"), Ingredient("3", "", "œufs"))))),
    )

    private fun roundTrip(i: SearchIndex): SearchIndex {
        val bytes = ByteArrayOutputStream().also { i.write(DataOutputStream(it)) }.toByteArray()
        return SearchIndex.read(DataInputStream(ByteArrayInputStream(bytes)))!!
    }

    @Test
    fun relectureIdentique() {
        val a = SearchIndex(recipes)
        val b = roundTrip(a)
        for (q in listOf("potiron", "crepe", "farine", "oigon", "soupes", "")) {
            assertEquals(q, a.search(q).map { it.entry.id to it.score }, b.search(q).map { it.entry.id to it.score })
        }
        assertEquals(a.categories, b.categories)
        assertEquals(4.5, b.entries.first { it.id == "a" }.rating, 0.0)
        assertTrue(b.entries.first { it.id == "b" }.favorite)
    }

    @Test
    fun favorisSansRecalcul() {
        val i = SearchIndex(recipes).withFavorites(setOf("a"))
        assertEquals(listOf(true, false), i.entries.map { it.favorite })
        assertEquals(listOf("a"), i.search("potiron").map { it.entry.id })
    }
}
