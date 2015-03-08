package net.mm.composer.properties

import net.mm.composer.properties.PropertiesValidator.{PropertiesValidator,Error}
import net.mm.composer.relations.RelationRegistry
import net.mm.composer.utils.SeqTypeFilterSupport._


object PropertiesValidator {
  type Error = String
  type PropertiesValidator = Either[Error, Seq[Property]] => Either[Error, Seq[Property]]
}

class PropertiesValidatorImpl(clazz: Class[_])(implicit relationRegistry: RelationRegistry) extends PropertiesValidator {

  private def validate(properties: Seq[Property], clazz: Class[_]): Option[Error] = {
    val clazzFields = clazz.getDeclaredFields.map(_.getName).toSet

    val fieldsToCheck = properties.typeFilter[FieldProperty].map(_.name).toSet -- clazzFields ++ properties.typeFilter[RelationProperty].map(_.name)

    properties.filter(p => fieldsToCheck(p.name))
      .map(p => (p, relationRegistry.get(clazz, p.name)))
      .foldLeft[Option[Error]](None){
        case (None, (property: FieldProperty, relation)) =>
          relation.fold[Option[Error]](Some(s"Unknown field '${property.name}' for ${clazz.getSimpleName}"))(rel => None)
        case (None, (property: RelationProperty, relation)) =>
          relation.fold[Option[Error]](Some(s"Unknown relation '${property.name}' for ${clazz.getSimpleName}"))(rel =>
            validate(property.properties, rel.target)
          )
        case (error, _) => error
    }
  }

  override def apply(parsedProperties: Either[PropertiesValidator.Error, Seq[Property]]): Either[PropertiesValidator.Error, Seq[Property]] = {
    parsedProperties.right.flatMap{ properties =>
      validate(properties, clazz).toLeft(properties)
    }
  }
}