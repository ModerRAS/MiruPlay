package com.miruplay.tv.sync.rss

import com.miruplay.tv.clouddrive.CloudDriveFileInfo
import com.miruplay.tv.model.FilenameMetadataParser
import com.miruplay.tv.scanner.DefaultEpisodeDetector
import com.miruplay.tv.scanner.VideoDirectoryClassifier

internal class AndroidCloudDriveVideoClassifier(
    filenameMetadataParser: FilenameMetadataParser
) : CloudDriveVideoClassifier {
    private val classifier = VideoDirectoryClassifier(DefaultEpisodeDetector(), filenameMetadataParser)

    override fun classify(file: CloudDriveFileInfo): CloudDriveVideoClassification {
        val classification = classifier.classifyVideo(file.path, file.name)
        return CloudDriveVideoClassification(
            showName = classification.animeName,
            seasonNumber = classification.seasonNumber
        )
    }
}

