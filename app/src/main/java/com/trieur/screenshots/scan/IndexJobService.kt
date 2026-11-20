package com.trieur.screenshots.scan

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.trieur.screenshots.TrieurApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Indexe les nouvelles captures sans ouvrir l'appli.
 *
 * JobScheduler directement plutôt que WorkManager : quand un déclencheur « nouvelle image »
 * réveille l'appli fermée, WorkManager reprogramme toutes ses tâches au démarrage et annule
 * celle qui venait d'être lancée (constaté sur le Galaxy S24, Android 16).
 *
 * Deux tâches :
 * - OBSERVER : se lance quand une image est ajoutée au MediaStore. Un déclencheur de contenu
 *   ne sert qu'une fois, donc on le réarme après chaque passage ;
 * - PERIODIC : filet de sécurité toutes les 12 h, conservé après redémarrage du téléphone.
 *   Il relance aussi l'appli, ce qui réarme OBSERVER (perdu au redémarrage).
 */
class IndexJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Une coroutine par tâche : arrêter l'une (contrainte perdue) ne doit pas annuler l'autre. */
    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = scope.launch {
            if (hasMediaAccess(applicationContext)) {
                try {
                    // Une image ajoutée pendant le scan ne redéclenche rien (le déclencheur n'est
                    // réarmé qu'après) : tant que le MediaStore a changé entre-temps, on refait un tour.
                    var rounds = 0
                    do {
                        val before = mediaGeneration()
                        val added = (application as TrieurApp).scanner.scan { _, _ -> }
                        Log.i(TAG, "Indexation en arrière-plan : $added nouvelle(s) image(s)")
                    } while (before != null && mediaGeneration() != before && ++rounds < 3)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "Indexation en arrière-plan échouée", e)
                }
            }
            running.remove(params.jobId)
            jobFinished(params, false)
            // Réarmer seulement une fois la tâche terminée : reprogrammer le même identifiant
            // pendant qu'elle tourne l'arrêterait.
            if (params.jobId == OBSERVER_ID) armObserver(applicationContext)
        }
        return true
    }

    /** Arrêt imposé par le système (pour cette tâche seulement) : on reprendra plus tard. */
    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    /** Compteur de modifications du MediaStore (Android 11+), null avant. */
    private fun mediaGeneration(): Long? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.getGeneration(applicationContext, MediaStore.VOLUME_EXTERNAL_PRIMARY) else null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IndexJobService"
        // Identifiants élevés pour ne pas croiser ceux que des bibliothèques allouent depuis 0.
        private const val OBSERVER_ID = 7_310_001
        private const val PERIODIC_ID = 7_310_002

        /** À appeler au démarrage de l'appli. Ne touche pas à une tâche déjà programmée ou en cours. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(OBSERVER_ID) == null) armObserver(context)
            if (scheduler.getPendingJob(PERIODIC_ID) == null) {
                scheduler.schedule(
                    JobInfo.Builder(PERIODIC_ID, component(context))
                        .setPeriodic(TimeUnit.HOURS.toMillis(12))
                        .setRequiresBatteryNotLow(true)
                        .setPersisted(true)
                        .build()
                )
            }
        }

        private fun armObserver(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(OBSERVER_ID, component(context))
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                        )
                    )
                    // Regroupe une rafale de captures et laisse le temps au fichier d'être écrit.
                    .setTriggerContentUpdateDelay(TimeUnit.SECONDS.toMillis(5))
                    .setTriggerContentMaxDelay(TimeUnit.SECONDS.toMillis(30))
                    .build()
            )
        }

        private fun component(context: Context) = ComponentName(context, IndexJobService::class.java)
    }
}
