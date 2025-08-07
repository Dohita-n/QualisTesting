# Composant IframeTable - Améliorations CSS et Gestion des Modifications

## Problèmes Résolus

### 1. Problèmes CSS (Largeur, Overflow, Disparition des Inputs)

#### Problèmes identifiés :
- Les inputs disparaissaient parfois à cause de styles CSS conflictuels
- Largeur insuffisante des cellules du tableau
- Problèmes d'overflow horizontal
- Styles qui forçaient la largeur à 0

#### Solutions implémentées :

##### Styles CSS améliorés :
```css
/* Largeur minimale garantie pour les cellules */
table.min-w-full th,
table.min-w-full td {
  min-width: 120px;
  max-width: 300px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Styles spécifiques pour les inputs */
.table-input {
  width: 100% !important;
  min-width: 100px !important;
  max-width: 100% !important;
  display: block !important;
  visibility: visible !important;
  opacity: 1 !important;
}
```

##### Classes CSS ajoutées :
- `.table-container` : Conteneur principal avec gestion de l'overflow
- `.data-table` : Tableau avec layout fixe
- `.table-header` : En-têtes avec position sticky
- `.table-row` : Lignes avec transitions
- `.table-cell` : Cellules avec largeur contrôlée
- `.table-input` : Inputs avec largeur garantie

##### Responsive Design :
- Adaptation automatique pour les petits écrans
- Largeurs minimales réduites sur mobile
- Amélioration du scroll tactile

### 2. Mémorisation des Champs Modifiés avec Pagination

#### Fonctionnalités ajoutées :

##### Cache des modifications :
```typescript
private modificationsCache: Map<string, any> = new Map();
```

##### Sauvegarde automatique :
- Les modifications sont sauvegardées automatiquement lors du changement de page
- Clé unique générée pour chaque page basée sur les données
- Métadonnées complètes (valeur originale, nouvelle valeur, timestamp)

##### Restauration automatique :
- Les modifications sont restaurées lors du retour sur une page
- Préservation de l'état des champs modifiés
- Indicateurs visuels pour les champs modifiés

#### Méthodes publiques disponibles :

```typescript
// Obtenir toutes les modifications sauvegardées
getSavedModifications(): Map<string, any>

// Effacer toutes les modifications sauvegardées
clearSavedModifications(): void

// Obtenir les modifications de la page actuelle
getCurrentPageModifications(): any[]
```

### 3. Indicateurs Visuels

#### Champs modifiés :
- Bordure jaune pour les champs modifiés
- Tooltip affichant l'ancienne et la nouvelle valeur
- Classe CSS `.modified` appliquée automatiquement

#### États de validation :
- `.empty-value` : Cellules vides
- `.invalid-value` : Cellules avec validation échouée
- `.error` : Erreurs de validation
- `.success` : Validation réussie

### 4. Améliorations de Performance

#### Optimisations CSS :
- `table-layout: fixed` pour un meilleur contrôle des largeurs
- `-webkit-overflow-scrolling: touch` pour le scroll iOS
- Transitions CSS optimisées

#### Optimisations TypeScript :
- Détection de changements OnPush
- Cache des modifications avec Map
- Génération de clés uniques optimisée

## Utilisation

### Intégration dans un composant parent :

```typescript
@Component({
  selector: 'app-parent',
  template: `
    <app-iframe-table
      [headers]="headers"
      [data]="data"
      [datasetId]="datasetId"
      (dataChanged)="onDataChanged($event)"
      (rowModified)="onRowModified($event)"
    ></app-iframe-table>
  `
})
export class ParentComponent {
  onDataChanged(data: any[]) {
    // Gérer les données modifiées
  }
  
  onRowModified(event: any) {
    // Gérer la modification d'une ligne spécifique
    console.log('Ligne modifiée:', event);
  }
}
```

### Accès aux modifications sauvegardées :

```typescript
@ViewChild(IframeTableComponent) tableComponent!: IframeTableComponent;

// Obtenir toutes les modifications
const modifications = this.tableComponent.getSavedModifications();

// Obtenir les modifications de la page actuelle
const currentModifications = this.tableComponent.getCurrentPageModifications();

// Effacer toutes les modifications
this.tableComponent.clearSavedModifications();
```

## Compatibilité

- Angular 15+
- Tailwind CSS
- Support du mode sombre
- Responsive design
- Support tactile (iOS/Android)

## Notes Techniques

### Gestion de la mémoire :
- Le cache des modifications est automatiquement nettoyé lors de la destruction du composant
- Les clés de cache sont basées sur un hash des données pour éviter les conflits

### Performance :
- Utilisation de `ChangeDetectionStrategy.OnPush` pour optimiser les performances
- Cache des templates HTML pour éviter les régénérations inutiles
- Debouncing des événements de validation pour les gros datasets

### Accessibilité :
- Support des navigateurs par clavier
- Tooltips informatifs
- Contraste approprié pour le mode sombre 