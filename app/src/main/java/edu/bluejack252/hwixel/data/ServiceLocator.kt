package edu.bluejack252.hwixel.data

import android.content.Context
import edu.bluejack252.hwixel.data.repository.AuthRepository
import edu.bluejack252.hwixel.data.repository.AuthRepositoryImpl
import edu.bluejack252.hwixel.data.repository.ProjectRepository
import edu.bluejack252.hwixel.data.repository.ProjectRepositoryImpl
import edu.bluejack252.hwixel.data.repository.TaskRepository
import edu.bluejack252.hwixel.data.repository.TaskRepositoryImpl
import edu.bluejack252.hwixel.data.repository.TeamHealthRepository
import edu.bluejack252.hwixel.data.repository.TeamHealthRepositoryImpl
import edu.bluejack252.hwixel.data.repository.UserRepository
import edu.bluejack252.hwixel.data.repository.UserRepositoryImpl
import edu.bluejack252.hwixel.data.source.local.HwixelDatabase
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
            projectSource = ProjectFirebaseSource()
        ).also { taskRepository = it }
    }

    fun getTeamHealthRepository(): TeamHealthRepository {
        return teamHealthRepository ?: TeamHealthRepositoryImpl(
            source = GptApiSource()
        ).also { teamHealthRepository = it }
    }
}
