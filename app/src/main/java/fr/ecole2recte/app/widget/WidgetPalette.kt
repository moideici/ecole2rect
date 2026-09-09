package fr.ecole2recte.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import fr.ecole2recte.app.R
import fr.ecole2recte.app.core.settings.AppSettings
import fr.ecole2recte.app.core.settings.Contrast

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
 * au hasard du XML**. Le fond comme le texte sont posés ici, d'un même tenant. Le gabarit ne
 * porte plus que des valeurs de repli, qui ne doivent jamais rester visibles.
 *
 * S'y ajoute le choix de la personne : suivre le système, forcer le clair ou le sombre, ou
 * poser ses propres couleurs. Un widget vit devant un fond d'écran que l'application n'a pas
 * choisi ; suivre le thème du téléphone est un bon défaut, pas une vérité.
 */
class WidgetPalette private constructor(
    @ColorInt val surface: Int,
    @ColorInt val ink: Int,
    @ColorInt val muted: Int,
    @ColorInt val accent: Int,
    @DrawableRes val background: Int,
    /**
     * Le fond exact demandé, quand il ne correspond à aucun des deux gabarits.
     *
     * Il est appliqué en teinte, ce qui préserve les coins arrondis — mais `RemoteViews` ne
     * sait teinter un fond qu'à partir d'Android 12. En deçà, le widget garde le gabarit le
     * plus proche : une couleur approchée avec la bonne forme vaut mieux qu'une couleur
     * exacte dans un rectangle brut.
     */
    @ColorInt val backgroundTint: Int?,
) {
    companion object {

        fun of(context: Context, settings: AppSettings = AppSettings()): WidgetPalette {
            val systemDark = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val colors = settings.widgetColors
            val dark = colors.isDark(systemDark)

            val base = if (dark) {
                WidgetPalette(
                    surface = context.getColor(R.color.widget_surface_dark),
                    ink = context.getColor(R.color.widget_ink_dark),
                    muted = context.getColor(R.color.widget_muted_dark),
                    accent = context.getColor(R.color.widget_accent_dark),
                    background = R.drawable.widget_background_dark,
                    backgroundTint = null,
                )
            } else {
                WidgetPalette(
                    surface = context.getColor(R.color.widget_surface_light),
                    ink = context.getColor(R.color.widget_ink_light),
                    muted = context.getColor(R.color.widget_muted_light),
                    accent = context.getColor(R.color.widget_accent_light),
                    background = R.drawable.widget_background_light,
                    backgroundTint = null,
                )
            }
            if (!colors.custom) return base

            val surface = colors.background?.toInt() ?: base.surface
            val ink = colors.inkOrDefault()?.toInt() ?: base.ink
            return WidgetPalette(
                surface = surface,
                ink = ink,
                // Le texte secondaire n'est pas réglable : il se dérive du principal, ce qui
                // garantit qu'il reste lisible sur le fond choisi au lieu de disparaître.
                muted = fade(ink, surface),
                accent = base.accent,
                background = base.background,
                backgroundTint = colors.background?.toInt(),
            )
        }

        /**
         * Le texte secondaire : le principal rapproché du fond.
         *
         * Un simple alpha ne suffirait pas — `RemoteViews` peint sur un fond qui n'est pas
         * toujours opaque, et un gris translucide sur un fond d'écran chargé devient
         * illisible. On mélange donc réellement les deux couleurs.
         */
        @ColorInt
        fun fade(@ColorInt ink: Int, @ColorInt surface: Int, amount: Float = 0.38f): Int {
            fun blend(shift: Int): Int {
                val from = (ink shr shift) and 0xFF
                val to = (surface shr shift) and 0xFF
                return (from + (to - from) * amount).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
        }

        /**
         * Le fond d'un bloc de grille : la couleur de la matière, ramenée vers la surface.
         *
         * Un bloc peint à la couleur pleine de la matière rendrait son texte illisible et
         * ferait du widget un damier. Un cinquième de teinte suffit à identifier la matière
         * d'un regard tout en gardant l'encre lisible — c'est ce que fait un vrai emploi du
         * temps papier avec ses surligneurs.
         */
        @ColorInt
        fun block(@ColorInt accent: Int, @ColorInt surface: Int, amount: Float = 0.82f): Int =
            fade(accent, surface, amount)

        /** Vrai quand le contraste du couple choisi descend sous le seuil de lisibilité. */
        fun isRisky(settings: AppSettings): Boolean {
            val colors = settings.widgetColors
            if (!colors.custom) return false
            val background = colors.background ?: return false
            val ink = colors.inkOrDefault() ?: return false
            return Contrast.ratio(background, ink) < Contrast.MINIMUM_RATIO
        }
    }
}
