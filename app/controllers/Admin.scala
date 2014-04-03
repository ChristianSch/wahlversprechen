package controllers

import models._
import models.Rating._

import play.api.Logger
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._

import scala.collection.JavaConversions._

import views._

object Admin extends Controller with Secured {
	def prefs = IsAdmin { user => implicit request => 
		Ok(html.adminPrefs( Author.loadAll(), User.findAll(), Tag.loadAll(), user ))
	}

	def userForm(edit: Boolean) = Form(
		tuple(
			"email" -> email,
			"name" -> nonEmptyText,
			"password" -> text.verifying(
				"Das Passwort muss mindestens 8 Stellen, eine Zahl und ein Sonderzeichen enthalten.", 
				password => edit && password.isEmpty 
				 		|| 8<=password.length && password.exists(_.isDigit) && password.exists(!_.isLetterOrDigit)
			),
			"role" -> number(min=0, max = Role.maxId),
			"admin_email" -> email,
			"admin_password" -> text
		) verifying ("Ungültige Administrator E-Mail oder falsches Passwort", result => result match {
			case (_, _, password, role, admin_email, admin_password) => {
				User.authenticate(admin_email, admin_password).map(_.role == Role.Admin).getOrElse(false)
			}
		}))

	def newUser = IsAdmin { user => implicit request => 
		userForm(/*edit*/ false).bindFromRequest.fold(
			formWithErrors => BadRequest(formWithErrors.errors.head.message),
			{ case (email, name, password, role, _, _) => {
				User.create(email, name, password, Role(role))
				Ok("")
			} }
		) 
	}

	def editUser(id: Long) = IsAdmin { user => implicit request => 
		userForm(/*edit*/ true).bindFromRequest.fold(
			formWithErrors => BadRequest(formWithErrors.errors.head.message),
			{ case (email, name, password, role, _, _) => {
				User.edit(id, email, name, Some(password), Some(Role(role))) 
				Ok("")
			} }
		) 
	}

	val verifyAdminForm = Form(
		tuple(
			"admin_email" -> email,
			"admin_password" -> text
		) verifying ("Ungültige Administrator E-Mail oder falsches Passwort", result => result match {
				case (admin_email, admin_password) => {
					User.authenticate(admin_email, admin_password).map(_.role == Role.Admin).getOrElse(false)
				}
		}))

	def deleteUser(id: Long) = IsAdmin { user => implicit request => 
		verifyAdminForm.bindFromRequest.fold(
			formWithErrors => BadRequest(formWithErrors.errors.head.message),
			{ case(_, _) => {
				User.delete(id)
				Ok("")
			}}
		) 
	}

	def authorForm(edited: Option[Long]) = Form(
		tuple(
			"name" -> nonEmptyText.verifying(
				"Ein Author mit diesem Namen existiert bereits", 
				name => { 
					Author.load(name).map(_.id) match {
						case None => true
						case Some(id) => edited==Some(id)
					}
				} 
			), 
			"order" -> number,
			"rated" -> boolean.verifying(
				"Es kann momentan nur einen ausgezeichneten Author geben, dessen Wahlversprechen bewertet werten.",
				rated => { 
					!rated || (Author.loadRated().map(_.id) match {
						case None => true
						case Some(id) => edited == Some(id)
					})
				}
			),
			"color" -> text,
			"background" -> text 
			)
		);

	def newAuthor() = IsAdmin { user => implicit request => 
		authorForm(None).bindFromRequest.fold(
			formWithErrors => BadRequest(formWithErrors.errors.head.message),
			{ case (name, order, rated, color, background) => {
				Author.create(name, order, rated, color, background) 
				Ok("")
			} }
		) // bindFromRequest
	}

	def editAuthor(id: Long) = IsAdmin { user => implicit request => 
		authorForm(Some(id)).bindFromRequest.fold(
			formWithErrors => BadRequest(formWithErrors.errors.head.message),
			{ case (name, order, rated, color, background) => {
				Author.edit(id, name, order, rated, color, background) 
				Ok("")
			} }
		) // bindFromRequest
	}

	val updateTagForm = Form(
		tuple(
			"name" -> optional(text), 
			"important" -> optional(boolean)
		))

	def updateTag(id: Long) = IsAdmin { user => implicit request =>
		Logger.debug("updateTag: " + id)
		updateTagForm.bindFromRequest.fold(
			formWithErrors => InternalServerError(""),
			{
				case (name, important) => {
					if(important.isDefined) Tag.setImportant(id, important.get)
					if(name.isDefined) Tag.setName(id, name.get)
				}
			})
		Ok("")
	}

	def deleteTag(id: Long) = IsAdmin { user => implicit request =>
		Tag.delete(id)
		Ok("")
	}

	val importForm = Form(
		tuple(
			"author" -> text.verifying("Unbekannter Autor", author => Author.load(author).isDefined),
			"spreadsheet" -> nonEmptyText))

	def viewImportForm  = IsAdmin { user => implicit request =>
		Ok(html.importview(importForm, user))
	}

	def doImport = IsAdmin { user => implicit request =>
		importForm.bindFromRequest.fold(
			formWithErrors => BadRequest(""),
			{ case (author_name, spreadsheet) => Import.loadSpreadSheet(author_name, spreadsheet) }
		) // bindFromRequest
	}
}