package br.com.civic.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Launcher padrão (a "tela inicial" do Android), como os launchers da Play Store: ao ligar a multimídia e ao tocar em Início,
 * o Android abre o Civic Renatin como tela inicial (não como um app por cima do launcher do fabricante, que aparece nos recentes).
 *  - Android 10+: pede o papel de tela inicial pelo caminho oficial (RoleManager); a escolha continua valendo depois das atualizações do app.
 *  - Antes disso (ou se a multimídia não oferecer o pedido): abre a tela do Android onde se escolhe o app de tela inicial.
 */
object LauncherPadrao {
    private fun intentInicio(ctx: Context) = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun ehPadrao(ctx: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        ctx.packageManager.resolveActivity(intentInicio(ctx), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
    }.getOrDefault(false)

    /** Intent que pede ao Android para tornar o app a tela inicial (ou abre a escolha manual). */
    fun intentDefinir(ctx: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    /** Traz o launcher como tela inicial (e não como app). */
    fun abrirComoInicio(ctx: Context) {
        val i = intentInicio(ctx).setPackage(ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
            .onFailure { runCatching { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}
