package com.trieur.screenshots.data

/**
 * Étiquettes du modèle ML Kit Image Labeling (base, hors ligne) : traduction française pour la
 * recherche (« chien » trouve les photos de chiens) et catégorie éventuelle de la photo.
 *
 * Format : « Étiquette anglaise = mots français = code », code de catégorie :
 * P personnes, A animaux, F nourriture, N nature, L lieux, D documents, R achats (ticket), vide = aucune.
 * Les étiquettes absentes restent cherchables en anglais.
 */
object Labels {
    /** En dessous, l'étiquette est trop incertaine pour décider de la catégorie. */
    const val CATEGORY_MIN_CONFIDENCE = 0.7f

    /** Seuil pour garder une étiquette (recherche). */
    const val KEEP_MIN_CONFIDENCE = 0.6f

    private data class Info(val french: String, val category: Category?)

    private val TABLE = """
        Person=personne gens=P
        Selfie=selfie=P
        Smile=sourire=P
        Laugh=rire=P
        Baby=bébé enfant=P
        Crowd=foule gens=P
        Team=équipe=P
        Bride=mariée mariage=P
        Groom=marié mariage=P
        Marriage=mariage=P
        Grandparent=grands-parents famille=P
        Dude=homme=P
        Beard=barbe=P
        Moustache=moustache=P
        Graduation=diplôme remise=P
        Prom=bal=P
        Dance=danse=P
        Musician=musicien=P
        Singer=chanteur=P
        Crew=équipe=P
        Model=mannequin=P
        Hair=cheveux=
        Eyelash=cils=
        Mouth=bouche=
        Hand=main=
        Foot=pied=
        Toe=orteil=
        Ear=oreille=
        Tattoo=tatouage=
        Bird=oiseau=A
        Cat=chat=A
        Dog=chien=A
        Pet=animal de compagnie=A
        Horse=cheval=A
        Insect=insecte=A
        Butterfly=papillon=A
        Bear=ours=A
        Cattle=vache bétail=A
        Herd=troupeau=A
        Duck=canard=A
        Turtle=tortue=A
        Crocodile=crocodile=A
        Penguin=pingouin manchot=A
        Seal=phoque=A
        Bull=taureau=A
        Gerbil=gerbille=A
        Dalmatian=dalmatien chien=A
        Shetland sheepdog=berger chien=A
        Cairn terrier=terrier chien=A
        Basset hound=basset chien=A
        Shikoku=chien=A
        Cavalier=cavalier chien=A
        Ragdoll=chat=A
        Sphynx=sphynx chat=A
        Pixie-bob=chat=A
        Himalayan=himalayen chat=A
        Pomacentridae=poisson=A
        Waterfowl=oiseau aquatique=A
        Larva=larve=A
        Aquarium=aquarium poisson=A
        Fur=fourrure=
        Food=nourriture repas=F
        Cuisine=cuisine plat=F
        Meal=repas=F
        Lunch=déjeuner repas=F
        Supper=dîner repas=F
        Fast food=fast-food=F
        Pizza=pizza=F
        Sushi=sushi=F
        Cake=gâteau=F
        Cookie=biscuit cookie=F
        Bread=pain=F
        Fruit=fruit=F
        Vegetable=légume=F
        Juice=jus=F
        Coffee=café=F
        Cappuccino=cappuccino café=F
        Wine=vin=F
        Alcohol=alcool=F
        Cola=soda=F
        Hot dog=hot-dog=F
        Cheeseburger=burger hamburger=F
        Bento=bento=F
        Couscous=couscous=F
        Pho=pho soupe=F
        Pie=tarte=F
        Gelato=glace=F
        Icing=glaçage gâteau=F
        Pasteles=pâtisserie=F
        Eating=manger repas=F
        Menu=menu restaurant=F
        Steaming=plat chaud=F
        Picnic=pique-nique=F
        Flesh=viande=F
        Tableware=vaisselle=
        Cutlery=couverts=
        Cup=tasse=
        Saucer=soucoupe=
        Sky=ciel=N
        Sunset=coucher de soleil=N
        Beach=plage mer=N
        Mountain=montagne=N
        Lake=lac=N
        River=rivière=N
        Forest=forêt=N
        Jungle=jungle=N
        Waterfall=cascade=N
        Glacier=glacier=N
        Iceberg=iceberg=N
        Cliff=falaise=N
        Canyon=canyon=N
        Desert=désert=N
        Dune=dune=N
        Volcano=volcan=N
        Cave=grotte=N
        Reef=récif=N
        Underwater=sous-marin=N
        Rainbow=arc-en-ciel=N
        Aurora=aurore boréale=N
        Moon=lune=N
        Star=étoile=N
        Nebula=nébuleuse=N
        Comet=comète=N
        Space=espace=
        Fog=brouillard=N
        Storm=orage tempête=N
        Lightning=éclair=N
        Field=champ=N
        Prairie=prairie=N
        Swamp=marais=N
        Sand=sable=N
        Rock=rocher=N
        Plant=plante=N
        Flower=fleur=N
        Petal=pétale fleur=N
        Flora=flore végétation=N
        Garden=jardin=N
        Branch=branche=N
        Twig=brindille=N
        Ice=glace=N
        Icicle=stalactite=N
        Flowerpot=pot de fleurs plante=N
        Skyline=ville horizon=L
        Skyscraper=gratte-ciel=L
        Building=bâtiment immeuble=L
        Tower=tour=L
        Bridge=pont=L
        Church=église=L
        Cathedral=cathédrale=L
        Mosque=mosquée=L
        Temple=temple=L
        Castle=château=L
        Palace=palais=L
        Monument=monument=L
        Museum=musée=L
        Ruins=ruines=L
        Lighthouse=phare=L
        Statue=statue=L
        Stadium=stade=L
        Park=parc=L
        Pier=jetée=L
        Ferris wheel=grande roue=L
        Dam=barrage=L
        Farm=ferme=L
        Barn=grange=L
        Receipt=ticket reçu=R
        Money=argent billets=R
        Passport=passeport=D
        Paper=papier document=D
        Newspaper=journal=D
        News=actualité=
        Poster=affiche=
        Whiteboard=tableau blanc=D
        Blackboard=tableau=D
        Screenshot=capture d'écran=
        Web page=page web=
        Icon=icône=
        Comics=bd bande dessinée=
        Car=voiture=
        Vehicle=véhicule=
        Bicycle=vélo=
        Motorcycle=moto=
        Bus=bus=
        Train=train=
        Van=camionnette=
        Boat=bateau=
        Sailboat=voilier bateau=
        Speedboat=hors-bord bateau=
        Airplane=avion=
        Airliner=avion=
        Aircraft=avion=
        Helicopter=hélicoptère=
        Rocket=fusée=
        Tractor=tracteur=
        Road=route=
        Wheel=roue=
        Tire=pneu=
        Bedroom=chambre=
        Bathroom=salle de bain=
        Kitchen=cuisine=
        Room=pièce=
        Couch=canapé=
        Chair=chaise=
        Desk=bureau=
        Shelf=étagère=
        Pillow=oreiller=
        Curtain=rideau=
        Television=télévision=
        Computer=ordinateur=
        Mobile phone=téléphone=
        Piano=piano=
        Musical instrument=instrument de musique=
        Toy=jouet=
        Stuffed toy=peluche=
        Plush=peluche=
        Lego=lego=
        Balloon=ballon=
        Christmas=noël=
        Santa claus=père noël=
        Party=fête soirée=
        Concert=concert=
        Fireworks=feu d'artifice=
        Carnival=carnaval=
        Vacation=vacances=
        Camping=camping=
        Sports=sport=
        Soccer=football foot=
        Swimming=natation piscine=
        Pool=piscine=
        Running=course=
        Skiing=ski=
        Surfing=surf=
        Shoe=chaussure=
        Sneakers=baskets=
        Dress=robe=P
        Jeans=jean=P
        Jacket=veste=P
        Hat=chapeau=P
        Cap=casquette=P
        Glasses=lunettes=P
        Sunglasses=lunettes de soleil=P
        Handbag=sac à main=
        Bag=sac=
        Necklace=collier=
        Ring=bague=
        Jewellery=bijoux=
        Heart=cœur=
        Love=amour=
        Flag=drapeau=
        Clock=horloge=
        Umbrella=parapluie=
        Fire=feu=
        Bonfire=feu de camp=
        School=école=
        Class=classe=
        Presentation=présentation=
        Tuxedo=smoking=P
        Gown=robe de soirée=P
        Leggings=legging=P
        Tie=cravate=P
        Beanie=bonnet=P
        Scarf=écharpe=P
        Blazer=blazer veste=P
        Outerwear=manteau=P
        Swimwear=maillot de bain=P
        Shorts=short=P
    """.trimIndent()

    private val INFO: Map<String, Info> = TABLE.lines()
        .mapNotNull { line ->
            val parts = line.split('=')
            if (parts.size != 3) return@mapNotNull null
            val category = when (parts[2].trim()) {
                "P" -> Category.PEOPLE
                "A" -> Category.ANIMAL
                "F" -> Category.FOOD
                "N" -> Category.NATURE
                "L" -> Category.PLACE
                "D" -> Category.DOCUMENT
                "R" -> Category.PURCHASE
                else -> null
            }
            parts[0].trim() to Info(parts[1].trim(), category)
        }
        .toMap()

    /**
     * Ordre de priorité quand une photo porte plusieurs indices nets : le sujet l'emporte sur le décor.
     * « Plante » et « Ciel » apparaissent en arrière-plan de presque tout (mesuré : 239 photos sur 728
     * classées Nature par simple confiance) ; une personne dehors est une photo de personne.
     */
    private val PRIORITY = listOf(
        Category.PURCHASE, Category.DOCUMENT, Category.ANIMAL, Category.FOOD,
        Category.PEOPLE, Category.PLACE, Category.NATURE,
    )

    /** Catégorie du sujet principal (null si aucune étiquette n'est assez nette). */
    fun categoryFor(labels: List<Label>): Category? {
        val found = labels.filter { it.confidence >= CATEGORY_MIN_CONFIDENCE }
            .mapNotNullTo(HashSet()) { INFO[it.name]?.category }
        return PRIORITY.firstOrNull { it in found }
    }

    /** Mots ajoutés à l'index de recherche : français puis anglais. */
    fun searchTags(labels: List<Label>): String =
        labels.flatMap { listOfNotNull(INFO[it.name]?.french, it.name.lowercase()) }
            .distinct()
            .joinToString(" ")

    /** Libellés français à afficher (ou anglais à défaut), du plus sûr au moins sûr. */
    fun display(labels: List<Label>): List<String> =
        labels.sortedByDescending { it.confidence }
            .map { INFO[it.name]?.french?.substringBefore(' ') ?: it.name.lowercase() }
            .distinct()

    /** Étiquette du modèle et sa confiance (0..1). */
    data class Label(val name: String, val confidence: Float)

    /** Stockage en base : « Dog:0.93|Pet:0.88 » ; chaîne vide = image analysée, rien trouvé. */
    fun encode(labels: List<Label>): String =
        labels.joinToString("|") { "${it.name}:${"%.2f".format(java.util.Locale.ROOT, it.confidence)}" }

    fun decode(stored: String?): List<Label> =
        stored.orEmpty().split('|').mapNotNull { part ->
            val i = part.lastIndexOf(':')
            if (i <= 0) null else part.substring(i + 1).toFloatOrNull()?.let { Label(part.substring(0, i), it) }
        }
}
