# Guide de débogage - Upload Excel

## Problème
Erreur 500 Internal Server Error lors de l'upload de fichiers Excel (.xlsx)

## Étapes de diagnostic

### 1. Vérifier les logs du serveur
```bash
cd server
mvn spring-boot:run
```

Puis uploader un fichier Excel et vérifier les logs pour identifier l'erreur exacte.

### 2. Points de vérification

#### A. Vérifier que ExcelFileProcessor est bien activé
- Dans `DataProcessingService.java`, vérifier que `ExcelFileProcessor` est déclaré et non commenté
- Vérifier que `case XLSX -> excelFileProcessor;` est présent dans `getProcessorForFileType()`

#### B. Vérifier les dépendances Apache POI
- Vérifier que les dépendances POI sont présentes dans `pom.xml`
- Vérifier les versions : `poi` et `poi-ooxml` version 5.4.1

#### C. Vérifier la validation Excel
- Dans `FileValidationService.java`, vérifier que `validateExcelSchema()` est activée
- Vérifier que `case XLSX: return validateExcelSchema(filePath);` est présent

### 3. Tests à effectuer

#### Test 1 : Fichier Excel simple
Créer un fichier Excel avec :
- En-têtes : Name, Age, City
- Données : 2-3 lignes de test
- Format : .xlsx

#### Test 2 : Vérifier les types de cellules
Tester avec différents types de données :
- Texte
- Nombres
- Dates
- Booléens
- Cellules vides

### 4. Corrections appliquées

#### A. Amélioration de la gestion d'erreurs
- Ajout de logs détaillés dans `ExcelFileProcessor`
- Gestion robuste des types de cellules
- Gestion des cellules vides et d'erreur

#### B. Amélioration du contrôleur
- Ajout de logs pour tracer l'upload
- Gestion spécifique des `IllegalArgumentException`
- Retour d'erreurs plus détaillées

#### C. Tests unitaires
- Tests pour `ExcelFileProcessor`
- Tests d'intégration pour l'upload

### 5. Commandes de test

```bash
# Compiler le projet
mvn clean compile

# Lancer les tests
mvn test

# Lancer le serveur
mvn spring-boot:run
```

### 6. Logs à surveiller

Rechercher dans les logs :
- `Processing Excel file:`
- `Error processing Excel file:`
- `Created X columns for dataset`
- `Successfully processed Excel file:`

### 7. Problèmes courants

1. **Fichier corrompu** : Vérifier que le fichier Excel est valide
2. **Feuille vide** : Vérifier qu'il y a des données dans la première feuille
3. **En-têtes manquants** : Vérifier qu'il y a une ligne d'en-tête
4. **Types de cellules non supportés** : Vérifier les types de données

### 8. Solution de contournement

Si le problème persiste, créer un fichier Excel simple avec :
- Une seule feuille
- Des en-têtes en première ligne
- Des données simples (texte, nombres)
- Pas de formules complexes
- Pas de formats spéciaux 