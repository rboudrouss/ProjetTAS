# LiSA tutorials TAS 2026 Project

AUTHORS :

- BOUDROUSS Réda

## Project : Implémentation de domaines abstraits

### Domaines implémentés

#### 1. KarrDomain (relationnel)

**Classe** : `KarrDomain.java`  

Domaine des égalités affines de Karr. Un élément abstrait représente l'ensemble des valuations satisfaisant un système d'équations :

```
a₁·x₁ + a₂·x₂ + ... + aₙ·xₙ + c = 0
```

##### Représentation interne

La matrice de contraintes est maintenue en **forme échelonnée réduite (RREF)**. La RREF est une forme canonique qui rend la comparaison de deux états et le test d'appartenance à l'espace ligne directs. Les variables sont stockées dans une liste ordonnée, les lignes de la matrice utilisent cette liste pour leurs indices de colonnes. Une colonne supplémentaire encode le terme constant.

| État | Représentation |
|---|---|
| `⊥` | flag `isBottomFlag = true` (ligne `0·x₁+...+0·xₙ + c = 0` avec c ≠ 0 détectée) |
| `⊤` | liste de contraintes vide |
| état normal | k lignes RREF (k ≤ n), chacune une équation affine indépendante |

##### Ordre du treillis

`D1 ⊑ D2` ssi chaque ligne de D2 est dans l'espace ligne de D1 : chaque contrainte de D2 est impliquée par D1.

##### Opérations clés

**LUB (join) : algorithme de Zassenhaus**

Une contrainte est valide après un join ssi elle est impliquée par les deux branches, i.e. elle appartient à l'intersection des deux espaces lignes R(M1) ∩ R(M2).

L'algorithme de Zassenhaus calcule cette intersection. Il forme la matrice augmentée :

```
Z = [ M1 | M1 ]
    [ M2 |  0 ]
```

puis effectue une élimination de Gauss sur la moitié gauche uniquement. Les lignes dont la moitié gauche est nulle ont leur moitié droite dans R(M1) ∩ R(M2).

Le lub supprime donc des contraintes (il correspond à une intersection d'espaces vectoriels, pas à une union).

**GLB (meet)**

Concaténation des lignes des deux systèmes + renormalisation RREF. Une ligne `0·x₁ + ... + 0·xₙ + c = 0` (c ≠ 0) dans le résultat produit `⊥`.

**Widening**

Widening = lub. Le treillis est de hauteur finie sur les entiers (au plus n contraintes indépendantes pour n variables) : les chaînes ascendantes convergent sans opérateur d'accélération.

**Affectation**

`tryLinear` parse récursivement l'expression. Deux cas :

1. **Expression linéaire** :
   - `x` absent de l'expression (`x := ay + b`) : projection de `x` par élimination de Gauss, puis ajout de la contrainte `x - ay - b = 0`.
   - `x` présent des deux côtés (`x := x + 1`) : substitution de l'ancienne valeur de `x` dans chaque contrainte, sans projection préalable. Une projection détruirait les contraintes impliquant `x`.

2. **Expression non-linéaire** : projection de `x`, toute information sur cette variable est perdue.

**Arithmétique flottante**

L'algèbre linéaire est effectuée en double précision (`double`), avec seuil `ε = 1e-9` pour les tests à zéro. L'arithmétique rationnelle exacte n'est pas utilisée.


#### 2. SetOfFloatValuesWithOverflow (non-relationnel)

**Classe** : `SetOfFloatValuesWithOverflow.java`  

Ensemble fini de valeurs `float` concrètes possibles pour une variable.

| Représentation | Signification |
|---|---|
| `⊤` (`values == null`) | ensemble non borné ou overflow |
| `⊥` (`values == {}`) | chemin inatteignable |
| `{v₁, v₂, ...}` | ces valeurs exactement sont possibles |

##### Borne MAX\_N\_ELEMENTS

`MAX_N_ELEMENTS = 5`. Si une opération produit plus de 5 valeurs, le résultat est `⊤`. Les opérations binaires sur les ensembles sont en O(|A| × |B|). Sans cette borne, la taille des ensembles n'est pas bornée.

##### Représentation de TOP

`⊤` est représenté par `values == null`. `⊥` est l'ensemble vide `{}`. `isTop()` est un test d'identité de référence (`this == TOP`).

##### Overflow

Tout résultat `Infinity` ou `NaN` retourne `⊤`. Ces valeurs n'appartiennent pas au domaine de représentation. Retourner `⊤` est correct par sur-approximation.

##### Division par zéro

En Java, la division flottante par zéro ne lève pas d'exception : IEEE 754 définit `x/0.0f = ±Infinity` et `0.0f/0.0f = NaN`. Ces résultats sont attrapés par le check overflow (`isInfinite || isNaN`) et retournent `⊤`. Il n'y a donc pas de traitement spécial pour `r == 0.0f` : le cas est géré uniformément avec les autres overflows.

##### Assume

L'assume filtre l'ensemble des valeurs selon la contrainte. Le flag `leftIsId` distingue `x < 3` de `3 < x`. Quand l'état est `⊤`, seul `==` produit un raffinement (vers un singleton). Les autres opérateurs laissent l'état inchangé.

#### 3. EqualityDomain (hors-sujet)

> Ce domaine a été implémenté en bonus (par erreur dans un premier temps).

Domaine non-relationnel qui trace les classes d'équivalence entre variables (`x == y`). Chaque variable est mappée à un ensemble d'identifiants qui lui sont connus égaux. Le LUB intersecte les classes, le GLB les fusionne.

#### 4. Produits cartésiens

##### 4a. CartesianProductSetFloatKarr

- **Gauche** : `ValueEnvironment<SetOfFloatValuesWithOverflow>`
- **Droite** : `KarrDomain`

Produit cartésien sans réduction. Assign, assume, lub, glb sont appliqués indépendamment aux deux composantes. Aucune information n'est échangée entre elles.

##### 4b. CartesianProductSetFloatEquality (hors-sujet)

> Utilise `EqualityDomain`, implémenté par erreur, conservé mais hors-sujet.

- **Gauche** : `ValueEnvironment<SetOfFloatValuesWithOverflow>`
- **Droite** : `EqualityDomain`

Produit cartésien sans réduction. Si `EqualityDomain` sait `x == y` et `SetOfFloats` a `x ∈ {1.0, 2.0}`, la déduction `y ∈ {1.0, 2.0}` n'est pas faite.

##### 4c. ReducedCartesianProductSetFloatEquality (hors-sujet)

> Utilise `EqualityDomain`, implémenté par erreur, conservé mais hors-sujet.

Produit cartésien avec réduction, invoquée dans `mk` à chaque construction d'état. Pour chaque variable `x` :

1. Récupération de sa classe d'équivalence `{y₁, y₂, ...}` dans `EqualityDomain`.
2. Calcul du GLB des ensembles de flottants de `x` et de tous ses égaux.
3. Mise à jour de l'environnement de flottants pour `x`.

```
EqualityDomain : x == y
SetOfFloats    : x ∈ {1.0, 2.0},  y ∈ {2.0, 3.0}
Après réduction: x ∈ {2.0},       y ∈ {2.0, 3.0}
```

La réduction est asymétrique : seul `x` est mis à jour dans chaque itération de la boucle, pas ses égaux. Elle n'est pas itérée jusqu'au point fixe. Elle va dans un seul sens (flottants raffinés par les égalités) : si `x ∈ {2.0}` et `y ∈ {2.0}`, on ne déduit pas `x == y`.

### Programmes IMP de test

| Fichier | Domaine testé | Ce qu'il illustre |
|---|---|---|
| `inputs/karr.imp` | KarrDomain | Chaîne linéaire, mise à l'échelle, branchement (perte au join), invariant de boucle (`2*i = j`), constantes, réaffectation |
| `inputs/setoffloatvalues.imp` | SetOfFloatValuesWithOverflow | Arithmétique concrète, branches, division par zéro, boucle |
| `inputs/setfloatequality.imp` | CartesianProduct(SetFloat, Equality) | Interaction flottants-égalités, assume sur égalité de variables, boucle |

### Construction et exécution

```bash
# Requiert Java 17+
./gradlew test
# Les graphes d'analyse HTML sont écrits dans outputs/
```

### Utilisation de l'IA dans le projet.

- L'autocomplétion automatique de github copilot lors du code (GPT-5)
- Demander des explications pour le Kaar domain et des conseils pour implémenter. (Claude Sonnet 4.6)
- Pour documenter et rajouter des commentaires dans le fichier `KarrDomain.java` (Claude Sonnet 4.6)
