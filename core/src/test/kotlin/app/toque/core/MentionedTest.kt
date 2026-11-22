package app.toque.core

import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.Quantities
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionedTest {
    private val ing = listOf(
        Ingredient("120", "g", "oignon"), Ingredient("10", "g", "huile neutre"), Ingredient("1", "c. à café", "curry en poudre"),
        Ingredient("250", "g", "lait de coco"), Ingredient("2", "", "œufs"), Ingredient("1 ½", "kg", "pommes de terre"),
    )

    @Test
    fun ingredientsCitesSansQuantite() {
        val f = Quantities.mentioned("Mettre l'huile et l'oignon dans le bol, puis hacher.", ing)
        assertEquals(listOf("huile neutre" to 10, "oignon" to 120), f.map { it.label to it.grams })
    }

    @Test
    fun pluriel_et_rien_de_pesable() {
        assertEquals(listOf(1500), Quantities.mentioned("Ajouter la pomme coupée.", ing).map { it.grams })
        assertEquals(emptyList<Int>(), Quantities.mentioned("Ajouter les œufs et le curry.", ing).map { it.grams })
    }

    @Test
    fun pesables() {
        assertEquals(listOf("oignon", "huile neutre", "lait de coco", "pommes de terre"), Quantities.weighable(ing).map { it.first.name })
    }
}
