# Uživatelé TermIta a jejich očekávání

Nástroj pro správu pojmosloví.


Use casy ze skutečného světa jsou:


## IPR Praha
Vytváří se nový produkt (např. Metropolitní plán Prahy) který spoluvytváří experti z různých domén a vychází ze závazných legislativních dokumentů.
TermIt by měl být na straně IPR použit k tomu, aby uchoval pojmosloví pro Metropolitní plán.
Uživatelen TermIta je územní plánovač nebo legislativec - zaměstnanec IPR.

### Cíle
- TermIt spravuje pojmosloví Metropolitního plánu,
- TermIt obsahuje soubor s textem Metropolitního plánu,
- soubor s textem metropolitního plánu obsahuje výskyty pojmů jak z Metropolitního plánu, tak z jiných nadřazených slovníků (Pražské stavební předpisy, zákony ...)

### Problémy
IPR musí řešit dva typy problémů:
- pojmy, které jsou používány v Metropolitním plánu musí být vysvětleny tak, aby jim porozumněli experti z různých domén.
- pojmy, které jsou používány v Metropolitním plánu musí být funkčně napojeny na pojmy z nadřazené legislativy -> pojem může být použit v kontextu MPP, ale musí být jasné, odkud pochází a jaká je jeho definice.

### Featury
- Tvorba pojmosloví
- Popis pojmů a uvedení do kontextu
- Diskuze o coworking
- Mapování
- Vizualizace pojmosloví
- Sdílení
- Anotace dokumentů


## KODI
Projektový tým KODI na MV se snaží o tvorbu metodiky pro efektivní publikaci a popis otevřených dat. TermIt by měl v rámci této "výrobní linky" sloužit k tvorbě popisu otevřených datových sad. Důležité je napojení na další slovníky, především na legislativu a tzv. OFN - základní, do určité míry abstraktní, definice struktury datových sad. Uživatelem je např. obec, která publikuje otevřená data.

### Cíle
- TermIt umožňuje tvorbu názvosloví k popisu publikovaných datových sad otevřených dat,
- TermIt umožňuje hierarchické propojení pojmů mezi sebou a na pojmy z nadřazených slovníků,
- jedná se o tvorbu popisu datových sad.

### Problémy
- pojmy, které slouží k popisu datových sad je třeba uvést do kontextu podle významu; k tomu by především měly sloužit OFN a tím pádem by měly být perfektně propojené na legislativu, která by zároveň měla být dobře namodelovaná,
- datové sady by měly být anotovány pomocí (pojmů ze) slovníků; těžko říct, jestli by to mělo být součástí TermIta, ale tato funkcionalita je důležitá.

### Featury
- Tvorba popisu datové sady (pojmosloví + hierarchie)
- Napojení pojmů na OFN (příp. využití OFN jako formálního jazyka??),
- Anotace datových sad
- Publikace dokumentace datové sady
- možná mě ještě něco napadne...?

## ČAS
V případě České agentury pro standardizaci se jedná o vytvoření slovníku pro CCI, což je mezinárodní slovník pro BIM. Podle slov lidí z ČAS se o (České) podobě slovníku pořád jedná, počítáme tedy s tím, že v TermItu kromě tvorby slovníku bude probíhat i diskuze. Pojmy s definicemi a hierarchií jsou zatím vedeny v excelu.

### Cíle
- TermIt spravuje pojmy (vícejazyčný název a definice, hierarchie, příklady),
- TermIt umoýžňuje změnu labelů a definic (a možná i diskuzi k nim),
- pokud víme, tak pojmy nejsou vázány na národní/evropskou legislativu.

### Problémy
- pojmy jsou v rámci témat/domén identifikovány pomocí kódu, v různých doménách mohou mít pojmy stejné kódy, z kódu lze odvodit hierarchii; je tedy potřeba rozdělovat pojmy podle témat/domén; mezi tématy/doménami vztahy nepředpokládáme,
- asi mi trochu uniká, k čemu slovník bude sloužit (k anotaci datových sad?),
- pojmy by mělo být možné naimportovat v nějakém fomátu (CSV?) a zároveň i vyexportovat.

### Featury
- Import pojmosloví
- Editace pojmů (labely a popisy)
- Podpora vícejazyčnosti,
- Vizualizace
- Diskuze a coworking
- Export (?)

## FA GAČR
V projektu GAČR ve spolupráci s FA se chystáme vytvořit slovník popisující veřejný prostor a chování lidí v něm. TermIt v něm bude mít (asi) dva typy uživatelů -- architekty/územní plánovače, vytvářející model veřejného prostoru a někoho, kdo bude vytvářet slovník chování z dat získaných sběrem (gamifikace, dotazníky...).

### Cíle
- TermIt bude spravovat slovníky popisující prvky veřejného prostoru (obecně) a chování jeho uživatelů,
- TermIt bude těmito slovníky popisovat konkrétní veřejná prostranství a očekávané a skutečné využití těcjto prvků (bude to skutečně dělat TermIt?)

### Problémy
- mezi prvky veřejného prostoru a typy využití musí být specifické vztahy (možná řešit pomocí formálních jazyků),
- cílem projektu je hledání vztahů mezi skutečným a plánovaným využitím na základě konfigurace veřejného prostoru; konfigurace = anotovaný veřejný prostor s přiřazenými prvky; vztahy = konflikty a nesoulady, možná rozdíly

### Featury
- Tvorba pojmosloví
- Anotace prostorových dat (vektory, rastry)
- (Vyhledávání konfliktů)

## JVF DTM
Pracovní skupina okolo JVF DTM vytváří ontologii a následně se jí pokouší připojit ke slovníkům vycházejícím z platné legislativy (asi?). Bylo by lepší projít s Petrem, případně doplním po schůzce s JVF DTM dne 30. 4. 2021.

### Cíle
- Existují dvě ontologie, jedna popisuje reálný svět, druhá "metodiku" pořizování dat pro DTM. Skutečná data pak je potřeba mapovat na metodiku, která bude nějakým způsobem napojená na reálný svět.

### Problémy
- uživatelé vyžadují pojmy s více definicemi (hierarchicky), technicky to musí být řešeno hyperkategoriemi

### Featury
TBD
