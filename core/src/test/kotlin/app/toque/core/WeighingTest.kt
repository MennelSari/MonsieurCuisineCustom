package app.toque.core

import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.Ingredients
import app.toque.core.recipes.QtyUnit
import app.toque.core.recipes.Quantities
import app.toque.core.recipes.WeighState
import app.toque.core.recipes.WeighTarget
import app.toque.core.recipes.Weighing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeighingTest {
    private fun key(text: String) = Ingredients.lookup(text)?.key

    @Test
    fun ingredientsReconnus() {
        assertEquals("riz arborio", key("riz arborio"))
        assertEquals("riz", key("Riz"))
        assertEquals("lait de coco", key("lait de coco"))
        assertEquals("lait demi ecreme", key("Lait demi-écrémé"))
        assertEquals("huile d'olive", key("30 g d'huile d'olive"))
        assertEquals("sucre roux", key("Sucre roux"))
        assertEquals("sucre en poudre", key("sucre en poudre"))
        assertEquals("sucre", key("Sucre"))
        assertEquals("pommes de terre", key("Pommes de terre farineuses"))
        assertEquals("oeufs", key("Œufs"))
        assertEquals("chocolat noir", key("chocolat noir 70 %"))
        assertEquals("tomates", key("tomates bien mûres"))
        assertEquals("vin blanc", key("Versez le vin blanc"))
        assertEquals("champignons de paris", key("champignons de Paris émincés"))
        assertNull(Ingredients.lookup(""))
        assertNull(Ingredients.lookup("ustensile inconnu"))
    }

    @Test
    fun couleursDistinctes() {
        val tomate = Ingredients.lookup("tomates")!!.color
        val carotte = Ingredients.lookup("carottes")!!.color
        val eau = Ingredients.lookup("eau")!!
        assertTrue(tomate != carotte)
        assertTrue(eau.liquid)
        assertEquals(1.0, eau.density, 0.0)
        assertEquals(0.91, Ingredients.lookup("huile d'olive")!!.density, 0.001)
    }

    @Test
    fun quantitesDansLeTexte() {
        val q = Quantities.find("Versez 10 cl de vin blanc et 80 cl de bouillon de légumes.")
        assertEquals(2, q.size)
        assertEquals("vin blanc", q[0].label)
        assertEquals(QtyUnit.CL, q[0].unit)
        assertEquals(99, q[0].grams) // 100 ml × 0,99
        assertEquals("bouillon de légumes", q[1].label)
        assertEquals(800, q[1].grams)

        val h = Quantities.find("Raclez les parois, ajoutez 30 g d'huile d'olive et faites revenir 3 min à 100 °C.")
        assertEquals(1, h.size)
        assertEquals("huile d'olive", h[0].label)
        assertEquals(30, h[0].grams)

        val l = Quantities.find("Ajoutez 1,5 l d'eau, 2 kg de pommes de terre et ½ l de lait")
        assertEquals(listOf(1500, 2000, 515), l.map { it.grams })
        assertEquals(listOf("eau", "pommes de terre", "lait"), l.map { it.label })

        assertTrue(Quantities.find("Faites cuire 20 min à 90 °C, vitesse 1.").isEmpty())
        assertTrue(Quantities.find("Remplissez le bol de 3 litres d'eau").single().grams == 3000)
    }

    @Test
    fun nomComplete() {
        val ing = listOf(Ingredient("300", "g", "riz arborio"))
        val q = Quantities.find("Ajoutez 300 g de riz et mélangez.", ing)
        assertEquals("riz arborio", q.single().label)
    }

    @Test
    fun affichageEnCentilitres() {
        val t = WeighTarget.of("lait", 50.0, QtyUnit.CL, Ingredients.lookup("lait"))
        assertEquals(515, t.grams)
        assertEquals(50.0, t.display(515), 0.01)
        assertEquals(38.0, t.display(391), 0.1)
        val g = WeighTarget.grams("riz arborio", 300)
        assertNotNull(g.info)
        assertEquals(212.0, g.display(212), 0.0)
    }

    @Test
    fun etats() {
        assertEquals(9, Weighing.tolerance(300))
        assertEquals(5, Weighing.tolerance(100))
        assertEquals(2, Weighing.tolerance(5))
        assertEquals(WeighState.TODO, Weighing.state(300, 135))
        assertEquals(WeighState.NEAR, Weighing.state(300, 270))
        assertEquals(WeighState.OK, Weighing.state(300, 291))
        assertEquals(WeighState.OK, Weighing.state(300, 309))
        assertEquals(WeighState.OVER, Weighing.state(300, 310))
        assertEquals(WeighState.OK, Weighing.state(5, 6))
        assertEquals(WeighState.OVER, Weighing.state(5, 8))
    }
}
