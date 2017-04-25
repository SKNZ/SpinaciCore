package ensiwow.realm.handlers

import ensiwow.realm.entities.{CharacterInfo, Guid, GuidType, Position}
import ensiwow.realm.protocol.{PayloadHandler, PayloadHandlerFactory, ResponseCodes}
import ensiwow.realm.protocol.payloads.{ClientCharacterCreate, ClientCharacterCreateEntry, ServerCharacterCreate}
import ensiwow.realm.session.NetworkWorker

class CharacterCreateHandler extends PayloadHandler[ClientCharacterCreate] {

  def checkNameValidity(name: String): ResponseCodes.Value = {
    log.debug(name)
    if (name.isEmpty) {
      ResponseCodes.CharNameNoName
    } else if (name.length >= ClientCharacterCreateEntry.MaxNameLength) {
      ResponseCodes.CharNameFailure
    } else {
      ResponseCodes.CharNameSuccess
    }
  }

  override def process(payload: ClientCharacterCreate): Unit = {
    val response = checkNameValidity(payload.character.charInfo.name)

    response match {
      case ResponseCodes.CharNameSuccess =>
        CharacterInfo.addCharacter(CharacterInfo.apply(Guid(CharacterInfo.getNextId, GuidType.Player),
          Position.mxyzo(0, -8937.25488f, -125.310707f, 82.8524399f, 0.662107527f),
          payload.character.charInfo))
    }

    sender ! NetworkWorker.EventOutgoing(ServerCharacterCreate(response))
  }
}

object CharacterCreateHandler extends PayloadHandlerFactory[CharacterCreateHandler, ClientCharacterCreate]