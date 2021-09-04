<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# kotlin-builder-generator Changelog

## [Unreleased]

## [1.0.2.RC]

### Added
1. Default values for properties with KotlinBuiltinType or BigDecimal
2. Needed import statements for each property
3. null value for nullable fields
4. Package statement

## [1.0.3.RC]

### Added
1. Default values for properties with all types of date and time Type
2. Default value for property with enum type

## [1.0.4.RC]

Made plugin compatible with upcoming 2021 versions of Intellij

## [1.0.5.RC]

1. Create builder for classes which have enum types
2. Does not create duplicate builders
3. Made plugin compatible till 2029 intellij versions

## [2.0.0]

1. Upgrade dependencies
2. Use proper value instead of null for recognized nullable type
3. Does not create duplicate data classes even if one of the type is nullable
4. Import the enum instead of importing each element inside it, which leads to errors when elements have same name in two different enum
