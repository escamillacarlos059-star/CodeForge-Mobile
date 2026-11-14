package com.example.model

data class GitHubUser(
    val login: String,
    val name: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val scopes: List<String>
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val htmlUrl: String,
    val description: String
)

data class GitHubJobStep(
    val number: Int,
    val name: String,
    val status: String,
    val conclusion: String?
)

data class GitHubJob(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val startedAt: String?,
    val completedAt: String?,
    val steps: List<GitHubJobStep>
)

data class GitHubWorkflowRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val event: String,
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val runNumber: Int,
    val headSha: String
)

data class GitHubArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
    val expired: Boolean,
    val createdAt: String
)

data class DownloadedArtifact(
    val artifactId: Long,
    val artifactName: String,
    val localFilePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val isApk: Boolean,
    val isAab: Boolean
)
