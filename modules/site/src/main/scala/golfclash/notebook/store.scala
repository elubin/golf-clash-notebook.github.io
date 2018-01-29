/*
 * MIT License
 *
 * Copyright (c) 2018 golf-clash-notebook
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package golfclash.notebook

import scala.concurrent.{ Promise => ScalaPromise }

import io.circe.scalajs._
import io.circe.generic.auto._
import io.circe.syntax._
import golfclash.notebook.core._
import firebase._
import firebase.auth._
import monix.eval._

object store {

  lazy val db = {
    Firebase
      .app()
      .firestore()
  }

  def holeCollection(holeId: String) = {
    db.collection("notes")
      .doc(holeId)
      .collection(holeId)
  }

  def holeNotesForUser(user: User, holeId: String): Task[List[HoleNote]] = {

    val promise = ScalaPromise[List[HoleNote]]()

    holeCollection(holeId)
      .where("userId", "==", user.uid)
      .get()
      .`then`(
        querySnapshot => {
          promise.success(
            // TODO: Error handling!! For now we're just throwing away failed decodings...
            querySnapshot.docs
              .map(docSnapshot => decodeJs[HoleNote](docSnapshot.data()))
              .toList
              .collect { case Right(note) => note }
          )
        },
        error => promise.failure(new RuntimeException(error.message))
      )

    Task.fromFuture(promise.future)
  }

  def saveNote(note: HoleNote): Task[HoleNote] = {

    val promise = ScalaPromise[HoleNote]()

    val newId  = java.util.UUID.randomUUID.toString
    val toSave = note.copy(id = Some(newId))

    holeCollection(note.holeId)
      .add(toSave.asJson.asJsAny)
      .`then`(
        _ => promise.success(toSave),
        error => promise.failure(new RuntimeException(error.message))
      )

    Task.fromFuture(promise.future)
  }

  def updateNote(note: HoleNote): Task[HoleNote] = {

    val promise = ScalaPromise[HoleNote]()

    note.id match {
      case Some(noteId) => {
        holeCollection(note.holeId)
          .where("id", "==", noteId)
          .get()
          .`then`(
            querySnapshot => {
              querySnapshot.docs.foreach { docSnapshot =>
                docSnapshot.ref.update(note.asJson.asJsAny)
              }

              promise.success(note)
            },
            error => promise.failure(new RuntimeException(error.message))
          )
      }
      case None => {
        promise.failure(new RuntimeException("Can't update a note with no ID!"))
      }
    }

    Task.fromFuture(promise.future)
  }

  def deleteNote(note: HoleNote): Task[Unit] = {
    val promise = ScalaPromise[Unit]()

    note.id match {
      case Some(noteId) => {
        holeCollection(note.holeId)
          .where("id", "==", noteId)
          .get()
          .`then`(
            querySnapshot => {
              querySnapshot.docs.foreach { docSnapshot =>
                docSnapshot.ref.delete()
              }

              promise.success(())
            },
            error => promise.failure(new RuntimeException(error.message))
          )
      }
      case None => {
        promise.failure(new RuntimeException("Can't delete a note with no ID!"))
      }
    }

    Task.fromFuture(promise.future)
  }

}