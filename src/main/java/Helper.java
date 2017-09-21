import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public  class Helper {
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);


     /*Метод для подготовки значений перед проверкой
    В любом месте данных можно указать $ключ_из_стэша$ - такое значение автоматичиски (до выполнения всех преобразований) будет заменно на значение_из_стэша
    Остальное можно увидеть в коде
    Пример:
    Когда пользователь подготавливает значения
    |Подготавливаемое значение  |Правило                 |Ключ преобразованного значения| - эта строка отфильтруется
    |12345678912345678912       |SAVE                    |СчетДляПроверки               | - здесь просто сохраним значение из первого столбца в СТЭШ с ключом "СчетДляПроверки"
    |$СчетДляПроверки$          |SUBSTRING:0 5           |ЧастьСчета1                   | - здесь из значения 12345678912345678912  будет извлечена подстрока с указанными индексами начала и конца
    |$СчетДляПроверки$          |SUBSTRING:5             |ЧастьСчета2                   | - и т.д.
    |$ЧастьСчета1$.$ЧастьСчета2$|SAVE                    |СчетДляПроверки1              | - в стэш будет сохранено "12345.678" с ключом "СчетДляПроверки1"
    |$СчетДляПроверки$          |SCORE                   |СчетДляПроверки               |

    */


    public static void preparationValue(DataTable data) {
        LOGGER.info("Подготовливаем значения");
        data.asLists(String.class).stream()
                //Фильтруем "Комментарии" можно расширить своими
                .filter(rowData ->
                        !("[Подготавливаемое значение, Правило, Ключ преобразованного значения]".equals(rowData.toString())
                                ||//или
                                "[,,]".equals(rowData.toString())
//                                ||//можно добавить еще что нибудь

                        ))
                //Работа с каждой строкой таблицы
                .forEach(rowData -> {

                    String oldValue = "";//Ключ или значение старое
                    String rule = ""; //Правило преобразования
                    String ruleArg = "";//Параметр преобразования
                    String newKey = ""; //Ключ для сохранения получившегося значения

                    //В этом блоке заменяем все значения $ключ_из_стэша$ -> значение_из_стэша
                    //можно придумать и для XLS - тогда следует данный блок повторить заменив соответсвующие регулярки и getValueFormStash -> getParamByName
                    //Хотя я лично за то что бы где нибудь в before парсить XLS в Stash
                    for (int i = 0; i < 3; i++) {
                        String str = rowData.get(i);
                        //парсим значения стеша $значение$
                        Pattern stashPattern = Pattern.compile("\\$[^$]+\\$");
                        Matcher matcher = stashPattern.matcher(str);
                        StringBuilder parsedStashBuilder = new StringBuilder();
                        int pos = 0;
                        while (matcher.find()) {
                            parsedStashBuilder.append(str, pos, matcher.start());
                            pos = matcher.end();
                            parsedStashBuilder.append(getValueFromStash(matcher.group().replaceAll("\\$", "")));
                        }
                        parsedStashBuilder.append(str, pos, str.length());

                        //парсим значения xls @секция.значение@
                        Pattern xlsPattern = Pattern.compile("@[^@]+@");
                        matcher = xlsPattern.matcher(parsedStashBuilder);
                        StringBuilder parsedXlsBuilder = new StringBuilder();
                        pos = 0;
                        while (matcher.find()) {
                            parsedXlsBuilder.append(parsedStashBuilder, pos, matcher.start());
                            pos = matcher.end();
                            String[] sectionParam = matcher.group().replaceAll("@", "").split("\\.");
                            parsedXlsBuilder.append(Page.getParamByName(sectionParam[0], sectionParam[1]));
                        }
                        parsedXlsBuilder.append(parsedStashBuilder, pos, parsedStashBuilder.length());

                        switch (i) {
                            case 0:
                                oldValue = parsedXlsBuilder.toString();
                                break;
                            case 1:
                                rule = parsedXlsBuilder.toString();
                                break;
                            case 2:
                                newKey = parsedXlsBuilder.toString();
                                break;
                            default:
                                LOGGER.info("Заглушка, для будущих модификаций метода");
                                break;
                        }
                    }
                    /* Теперь работаем только со значениями:
                    oldValue - значение которое нужно изменить
                    rule - правило по которому нужно изменить
                    newKey - ключ под которым мы сохраним в СТЭШ
                     */
                    LOGGER.info("Подготавливаемое значение: "+ oldValue);
                    LOGGER.info("Правило: " + rule);
                    LOGGER.info("Ключ преобразованного значения: " + newKey);

                    if (rule.contains(":")) {//Rule может быть в виде Правило:Аргументы правила
                        ruleArg = rule.split(":")[1];//аргументы правила
                        rule = rule.split(":")[0];//Само правило
                    }
                    String newValue = "";
                    switch (rule) {
                        case "SAVE"://Просто сохраняем/пересохраняем значение в STASH с новым(а можно и прежним) ключом
                            newValue = oldValue;
                            break;

                        case "REPLACE"://ruleArgs: заменяемые_символы которыми_заменяем (если второй аргумент отсутствует -> заменяем "")
                            String[] oldNewChar = ruleArg.split(" ");
                            newValue = oldValue.replaceAll(oldNewChar[0],
                                    oldNewChar.length == 1 ? "" : oldNewChar[1]);
                            break;
                        case "TOLOWERCASE"://
                            newValue = oldValue.toLowerCase();
                            break;

                        case "SUBSTRING"://ruleArgs в данном случае должны быть: индекс_начала индекс_конца(если есть)
                            if (ruleArg.contains(" ")){
                                int beginIndex = Integer.parseInt(ruleArg.split(" ")[0]);
                                int endIndex = Integer.parseInt(ruleArg.split(" ")[1]);
                                newValue = oldValue.substring(beginIndex, endIndex);
                            } else {
                                newValue =  oldValue.substring(Integer.parseInt(ruleArg));
                            }
                            break;
                        //Генерация различного рода данных (можно добавить генерацию случайных строк и прочего)
                        case "Сгенерировать"://ruleArgs в данном случае должен быть: тип_генерируемого_значения
                            switch (ruleArg) {
                                case "СлучайныйНомерДокумента":
                                    newValue = getRandomDocNum(6);
                                    break;
                                default:
                                    LOGGER.info("Указанный тип генерируемого значения '" + ruleArg + "' не поддерживается");
                                    throw new AutotestError("Указанный тип генерируемого значения '" + ruleArg + "' не поддерживается");
                            }
                            break;

                        case "SPLIT"://ruleArgs в данном случае должны быть: символ по которому будем осуществлять разбиение#номер отрезка который хотим сохранить
                            String delimiter = ruleArg.split("#")[0];
                            int index = Integer.parseInt(ruleArg.split("#")[1]);
                            newValue = oldValue.split(delimiter)[index];
                            break;

                        case "INT":

                            break;

                        case "LOG"://Для дебага
                            LOGGER.info(oldValue);
                            break;

                        case "SCORE"://добавляем точки счету - обратная совместимость
                            newValue = getDottedScore(oldValue.trim());
                            break;

                        case "CLEAR_IMPORT_VALUE"://берет из массива импорта не пустое значение (вытаскивать номера и даты доков)
                            //CLEAR_IMPORT_VALUE - последнее значение. CLEAR_IMPORT_VALUE:index - значение по индексу
                            List<String> values = Arrays.asList(oldValue.replaceAll("[\\[\\]]", "").split(","))
                                    .stream().map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toList());
                            if (ruleArg.isEmpty())
                                newValue = values.get(values.size() - 1);
                            else
                                newValue = values.get(Integer.parseInt(ruleArg));
                            break;

//                      Блок кейсов для работы с датами
                        case "Текущая дата в формате"://Входной параметр не требуется, в параметрах должен быть указан паттерн форматирования
                            newValue = LocalDate.now().format(DateTimeFormatter.ofPattern(ruleArg));
                            if (ruleArg.contains("MMM") && !ruleArg.contains("MMMM"))
                                newValue = newValue.replaceAll("мая", "май");
                            break;
                        case "Превести формат"://ruleArgs в данном случае должны быть:текущий_формат->новый_формат
                            LOGGER.info("Меняем формат даты");
                            DateTimeFormatter currenFormat = DateTimeFormatter.ofPattern(ruleArg.split("->")[0]);
                            DateTimeFormatter newFormat = DateTimeFormatter.ofPattern(ruleArg.split("->")[1]);
                            newValue = LocalDate.parse(oldValue,currenFormat).format(newFormat);
                            break;
                        case "Прибавить к дате в формате"://oldValue в данном случае должен быть: дата,ruleArgs в данном случае должны быть:паттерн_даты разница_дат(может быть отрицательным) часть_даты(DAYS,MONTHS,YEARS)
                            String pattern = ruleArg.split(" ")[0];//Формат входной даты
                            int ammount = Integer.parseInt(ruleArg.split(" ")[1]);//Количество дней|месяце|лет которое будет прибавляться
                            String partOfDate = ruleArg.split(" ")[2];//указание к какой части даты следует прибавлять|отнимать ammount

                            DateTimeFormatter formatter3 = DateTimeFormatter.ofPattern(pattern);
                            LocalDate oldDate = LocalDate.parse(oldValue,formatter3);
                            LocalDate newDate;
                            switch (partOfDate){
                                case "DAYS":
                                    newDate = oldDate.plus(ammount, ChronoUnit.DAYS);
                                    break;
                                case "MONTHS":
                                    newDate = oldDate.plus(ammount, ChronoUnit.MONTHS);
                                    break;
                                case "YEARS":
                                    newDate = oldDate.plus(ammount, ChronoUnit.YEARS);
                                    break;//Исправлена опечатка.
//                                   Можно расширять
                                default:
                                    LOGGER.info("Указанная часть даты '" + partOfDate + "' не поддерживается");
                                    throw new AutotestError("Указанная часть даты '" + partOfDate + "' не поддерживается");
                            }
                            newValue = newDate.format(formatter3);
                            break;
//                        Конец блока по работе с датами

                        default:
                            LOGGER.info("Указанное правило '" + rule + "' еще не реализовано!");
                            throw new AutotestError("Указанное правило '" + rule + "' еще не реализовано!");
                    }
                    saveValueToStash(newKey, newValue);
                });
}

    private static String getRandomDocNum(int i) {
    }

    private static void saveValueToStash(String newKey, String newValue) {
    }

    private static char[] getValueFromStash(String s) {
    return s[];
    }

