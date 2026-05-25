package com.miruplay.tv.clouddrive

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrpcCloudDriveClient @Inject constructor() : CloudDriveClient by SharedGrpcCloudDriveClient()
