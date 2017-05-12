package wow.realm.handlers

import wow.realm.entities.{CharacterInfo, Position}
import wow.realm.protocol._
import wow.realm.protocol.payloads.{CharacterDescription, ClientCharacterCreate, ServerCharacterCreate}
import wow.realm.session.Session

/**
  * Handles character creation requests
  * If the character is valid, it will be added to the CharacterInfo list
  */
object CharacterCreateHandler extends PayloadHandler[Session, ClientCharacterCreate] {
  /**
    * Checks if the entered name is valid
    *
    * @param name the name of the desired character
    * @return the response code of the response packet
    */
  def checkNameValidity(name: String): CharacterCreationResults.Value = {
    if (name.isEmpty || name.length >= CharacterDescription.MaxNameLength) {
      CharacterCreationResults.Failed
    } else {
      CharacterCreationResults.Success
    }
  }

  /**
    * Processes the creation request packet
    *
    * @param payload the client's packet containing the information on the desired character
    */
  protected override def handle(header: ClientHeader, payload: ClientCharacterCreate)(self: Session): Unit = {
    val response = checkNameValidity(payload.character.charInfo.name)

    if (response == CharacterCreationResults.Success) {
      CharacterInfo.addCharacter(
        CharacterInfo(CharacterInfo.getNextGuid,
          Position.mxyzo(0, -8937.25488f, -125.310707f, 82.8524399f, 0.662107527f),
          payload.character.charInfo)
      )
    }

    self.sendPayload(ServerCharacterCreate(response))
  }
}
