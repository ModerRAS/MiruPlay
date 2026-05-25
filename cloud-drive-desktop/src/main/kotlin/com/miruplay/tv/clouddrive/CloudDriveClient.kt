package com.miruplay.tv.clouddrive

class GrpcCloudDriveClient(
    private val delegate: CloudDriveClient = SharedGrpcCloudDriveClient(),
) : CloudDriveClient by delegate
