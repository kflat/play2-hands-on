package controllers

import javax.inject.Inject
import play.api.db.slick._
import scala.concurrent.Future
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import models.Tables._
import slick.driver.H2Driver.api._
import play.api.libs.functional.syntax._
import JsonController._


object JsonController {

  // UsersRowをJSONに変換するためのWritesを定義
  // writesを自動生成してくれるマクロ => implicit val userFormWrites = Json.writes[UserForm]
  implicit val usersRowWritesWrites = (
    (__ \ "id"        ).write[Long]     and
    (__ \ "name"      ).write[String]   and
    (__ \ "companyId" ).writeNullable[Int]
  )(unlift(UsersRow.unapply))

  // ユーザ情報を受け取るためのケースクラス
  case class UserForm(id: Option[Long], name: String, companyId: Option[Int])

  // JsonをUserFormに変換するためのReadsを定義
  // readsを自動生成してくれるマクロ　=> implicit val userFormReads = Json.reads[UserForm]
  implicit val userFormFormat = (
    (__ \ "id"        ).readNullable[Long] and
    (__ \ "name"      ).read[String]       and
    (__ \ "companyId" ).readNullable[Int]
  )(UserForm)

  // ReadsとWritesの両方が必要な場合はJson.formatマクロを使うことができる
  // implicit val userFormFormat = Json.format[userForm]

}

class JsonController @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends Controller with HasDatabaseConfigProvider[JdbcProfile] {

  /**
    * 一覧表示
    */
  def list = Action.async { implicit rs =>
      db.run(Users.sortBy(t => t.id).result).map { users =>
      Ok(Json.obj("users" -> users))
    }
  }

  /**
   * ユーザ登録
   */
  def create = Action.async(parse.json) { implicit rs =>
    rs.body.validate[UserForm].map { form =>
      // Okの場合はユーザを登録
      val user = UsersRow(0, form.name, form.companyId)
      db.run(Users += user).map { _ =>
        Ok(Json.obj("result" -> "success"))
      }
    }.recoverTotal { e =>
      // NGの場合はバリデーションエラーを返す
      Future {
        BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e)))
      }
    }
  }

  /**
   * ユーザ更新
   */
  def update = Action.async(parse.json) { implicit rs =>
    rs.body.validate[UserForm].map { form =>
      // Okの場合はユーザ情報を更新
      val user = UsersRow(form.id.get, form.name, form.companyId)
      db.run(Users.filter(t => t.id === user.id.bind).update(user)).map { _ =>
        Ok(Json.obj("result" -> "success"))
      }
    }.recoverTotal { e =>
      // NGの場合はバリデーションエラーを返す
      Future {
        BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e)))
      }
    }
  }

  /**
   * ユーザ削除
   */
  def remove(id: Long) = Action.async { implicit rs =>
    // ユーザ情報を削除
    db.run(Users.filter(t => t.id === id.bind).delete).map { _ =>
      Ok(Json.obj("result" -> "success"))
    }
  }

}