package br.com.civic.launcher

import android.service.notification.NotificationListenerService

/**
 * Serviço vazio: existe só para o Android liberar o acesso às sessões de mídia
 * (ver MediaMonitor). O acesso é concedido em Configurações > Acesso às notificações.
 */
class MediaListenerService : NotificationListenerService()
