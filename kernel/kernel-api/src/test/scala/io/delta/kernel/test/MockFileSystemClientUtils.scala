/*
 * Copyright (2023) The Delta Lake Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.delta.kernel.test

import io.delta.kernel.client._
import io.delta.kernel.internal.fs.Path
import io.delta.kernel.internal.util.FileNames
import io.delta.kernel.internal.util.Utils.toCloseableIterator
import io.delta.kernel.utils.{CloseableIterator, FileStatus}

import scala.collection.JavaConverters._

/**
 * This is an extension to [[BaseMockFileSystemClient]] containing specific mock implementations
 * [[FileSystemClient]] which are shared across multiple test suite.
 *
 * [[MockListFromFileSystemClient]] - mocks the `listFrom` API within [[FileSystemClient]].
 */
trait MockFileSystemClientUtils extends MockTableClientUtils {

  val dataPath = new Path("/fake/path/to/table/")
  val logPath = new Path(dataPath, "_delta_log")

  /** Delta file statuses where the timestamp = 10*version */
  def deltaFileStatuses(deltaVersions: Seq[Long]): Seq[FileStatus] = {
    assert(deltaVersions.size == deltaVersions.toSet.size)
    deltaVersions.map(v => FileStatus.of(FileNames.deltaFile(logPath, v), v, v*10))
  }

  /** Checkpoint file statuses where the timestamp = 10*version */
  def singularCheckpointFileStatuses(checkpointVersions: Seq[Long]): Seq[FileStatus] = {
    assert(checkpointVersions.size == checkpointVersions.toSet.size)
    checkpointVersions.map(v =>
      FileStatus.of(FileNames.checkpointFileSingular(logPath, v).toString, v, v*10)
    )
  }

  /** Checkpoint file statuses where the timestamp = 10*version */
  def multiCheckpointFileStatuses(
    checkpointVersions: Seq[Long], numParts: Int): Seq[FileStatus] = {
    assert(checkpointVersions.size == checkpointVersions.toSet.size)
    checkpointVersions.flatMap(v =>
      FileNames.checkpointFileWithParts(logPath, v, numParts).asScala
        .map(p => FileStatus.of(p.toString, v, v*10))
    )
  }

  /* Create input function for createMockTableClient to implement listFrom from a list of
   * file statuses.
   */
  def listFromProvider(files: Seq[FileStatus])(filePath: String): Seq[FileStatus] = {
    files.filter(_.getPath.compareTo(filePath) >= 0).sortBy(_.getPath)
  }

  /**
   * Create a mock [[TableClient]] to mock the [[FileSystemClient.listFrom]] calls using
   * the given contents. The contents are filtered depending upon the list from path prefix.
   */
  def createMockFSListFromTableClient(contents: Seq[FileStatus]): TableClient = {
    mockTableClient(fileSystemClient =
      new MockListFromFileSystemClient(listFromProvider(contents)))
  }

  /**
   * Create a mock [[TableClient]] to mock the [[FileSystemClient.listFrom]] calls using
   * [[MockListFromFileSystemClient]].
   */
  def createMockFSListFromTableClient(listFromProvider: String => Seq[FileStatus]): TableClient = {
    mockTableClient(fileSystemClient = new MockListFromFileSystemClient(listFromProvider))
  }
}

/**
 * A mock [[FileSystemClient]] that answers `listFrom` calls from a given content provider.
 *
 * It also maintains metrics on number of times `listFrom` is called and arguments for each call.
 */
class MockListFromFileSystemClient(listFromProvider: String => Seq[FileStatus])
    extends BaseMockFileSystemClient {
  private var listFromCalls: Seq[String] = Seq.empty

  override def listFrom(filePath: String): CloseableIterator[FileStatus] = {
    listFromCalls = listFromCalls :+ filePath
    toCloseableIterator(listFromProvider(filePath).iterator.asJava)
  }

  def getListFromCalls: Seq[String] = listFromCalls
}