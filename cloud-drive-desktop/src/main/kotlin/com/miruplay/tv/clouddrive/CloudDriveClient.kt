package com.miruplay.tv.clouddrive

class GrpcCloudDriveClient(
    private val delegate: SharedGrpcCloudDriveClient = SharedGrpcCloudDriveClient(),
) : CloudDriveClient by delegate
