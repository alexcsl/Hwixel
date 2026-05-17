package edu.bluejack252.hwixel.data

import android.content.Context
import edu.bluejack252.hwixel.data.repository.AttendanceRepository
import edu.bluejack252.hwixel.data.repository.AttendanceRepositoryImpl
import edu.bluejack252.hwixel.data.repository.EvalRepository
import edu.bluejack252.hwixel.data.repository.EvalRepositoryImpl
import edu.bluejack252.hwixel.data.repository.FileRepository
import edu.bluejack252.hwixel.data.repository.FileRepositoryImpl
import edu.bluejack252.hwixel.data.repository.NotificationRepository
import edu.bluejack252.hwixel.data.repository.NotificationRepositoryImpl
import edu.bluejack252.hwixel.data.repository.AuthRepository
import edu.bluejack252.hwixel.data.repository.AuthRepositoryImpl
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.ProjectRepositoryImpl
import edu.bluejack252.hwixel.data.repository.ProfileSettingsRepository
import edu.bluejack252.hwixel.data.repository.SharedPrefsProfileSettingsRepository
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.TaskRepositoryImpl
import edu.bluejack252.hwixel.data.repository.TeamHealthRepository
import edu.bluejack252.hwixel.data.repository.TeamHealthRepositoryImpl
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.data.repository.UserRepositoryImpl
import edu.bluejack252.hwixel.data.source.local.HwixelDatabase
import edu.bluejack252.hwixel.data.source.remote.AttendanceFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.EvalFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.FileFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.NotificationFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.AuthFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.GptApiSource
import edu.bluejack252.hwixel.data.source.remote.ProjectFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.TaskFirebaseSource
import edu.bluejack252.hwixel.data.source.remote.UserFirebaseSource

object ServiceLocator {
    private var authRepository: AuthRepository? = null
    private var userRepository: UserRepository? = null
    private var projectRepository: ProjectRepository? = null
    private var taskRepository: TaskRepository? = null
    private var teamHealthRepository: TeamHealthRepository? = null
    private var attendanceRepository: AttendanceRepository? = null
    private var evalRepository: EvalRepository? = null
    private var fileRepository: FileRepository? = null
    private var notificationRepository: NotificationRepository? = null
    private var profileSettingsRepository: ProfileSettingsRepository? = null

    fun getAuthRepository(context: Context): AuthRepository {
        return authRepository ?: AuthRepositoryImpl(
            authFirebaseSource = AuthFirebaseSource(),
            userFirebaseSource = UserFirebaseSource(),
            userDao = HwixelDatabase.getInstance(context).userDao()
        ).also { authRepository = it }
    }

    fun getUserRepository(context: Context): UserRepository {
        return userRepository ?: UserRepositoryImpl(
            firebaseSource = UserFirebaseSource(),
            localDao = HwixelDatabase.getInstance(context).userDao()
        ).also { userRepository = it }
    }

    fun getProjectRepository(context: Context): ProjectRepository {
        return projectRepository ?: ProjectRepositoryImpl(
            firebaseSource = ProjectFirebaseSource(),
            localDao = HwixelDatabase.getInstance(context).projectDao()
        ).also { projectRepository = it }
    }

    fun getTaskRepository(context: Context): TaskRepository {
        return taskRepository ?: TaskRepositoryImpl(
            firebaseSource = TaskFirebaseSource(),
            localDao = HwixelDatabase.getInstance(context).taskDao(),
            projectSource = ProjectFirebaseSource(),
            notifSource = NotificationFirebaseSource(),
            userSource = UserFirebaseSource()
        ).also { taskRepository = it }
    }

    fun getTeamHealthRepository(): TeamHealthRepository {
        return teamHealthRepository ?: TeamHealthRepositoryImpl(
            source = GptApiSource()
        ).also { teamHealthRepository = it }
    }

    fun getAttendanceRepository(): AttendanceRepository {
        return attendanceRepository ?: AttendanceRepositoryImpl(
            firebaseSource = AttendanceFirebaseSource()
        ).also { attendanceRepository = it }
    }

    fun getEvalRepository(): EvalRepository {
        return evalRepository ?: EvalRepositoryImpl(
            firebaseSource = EvalFirebaseSource()
        ).also { evalRepository = it }
    }

    fun getFileRepository(): FileRepository {
        return fileRepository ?: FileRepositoryImpl(
            firebaseSource = FileFirebaseSource()
        ).also { fileRepository = it }
    }

    fun getNotificationRepository(): NotificationRepository {
        return notificationRepository ?: NotificationRepositoryImpl(
            source = NotificationFirebaseSource()
        ).also { notificationRepository = it }
    }

    fun getNotificationSource(): NotificationFirebaseSource = NotificationFirebaseSource()

    fun getProfileSettingsRepository(context: Context): ProfileSettingsRepository {
        return profileSettingsRepository ?: SharedPrefsProfileSettingsRepository(context)
            .also { profileSettingsRepository = it }
    }
}
