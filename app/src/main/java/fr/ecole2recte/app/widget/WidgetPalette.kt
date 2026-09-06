package fr.ecole2recte.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import fr.ecole2recte.app.R

/**
 * Les couleurs d'un widget, choisies par l'application et non par le lanceur.
 *
 * C'est le piège le plus coûteux de `RemoteViews` : le gabarit est inflaté **dans le
 * processus du lanceur**, donc une couleur écrite en XML (`@color/...`) y est résolue avec
 * *sa* configuration, tandis qu'une couleur posée par le code l'est avec la nôtre. Tant que
 * les deux sont d'accord, tout va bien ; le jour où elles divergent — lanceur en clair et
 * application en sombre, ou l'inverse — on obtient un fond blanc avec du texte blanc dessus.
 * Le widget paraît vide alors que tout y est.
 *
 * La parade est simple et vaut pour n'importe quel widget : **ne jamais laisser une couleur
 * au hasard du XML**. Le fond comme le texte sont posés ici, d'un même tenant, à partir de la
 * configuration de l'application. Le gabarit ne porte plus que des valeurs de repli, qui ne
 * doivent jamais rester visibles.
 */
class WidgetPalette private constructor(
    @ColorInt val surface: Int,
    @ColorInt val ink: Int,
    @ColorInt val muted: Int,
    @ColorInt val accent: Int,
    @DrawableRes val background: Int,
) {
    companion object {
        fun of(context: Context): WidgetPalette {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) {
                WidgetPalette(
                    surface = context.getColor(R.color.widget_surface_dark),
                    ink = context.getColor(R.color.widget_ink_dark),
                    muted = context.getColor(R.color.widget_muted_dark),
                    accent = context.getColor(R.color.widget_accent_dark),
                    background = R.drawable.widget_background_dark,
                )
            } else {
                WidgetPalette(
                    surface = context.getColor(R.color.widget_surface_light),
                    ink = context.getColor(R.color.widget_ink_light),
                    muted = context.getColor(R.color.widget_muted_light),
                    accent = context.getColor(R.color.widget_accent_light),
                    background = R.drawable.widget_background_light,
                )
            }
        }
    }
}
