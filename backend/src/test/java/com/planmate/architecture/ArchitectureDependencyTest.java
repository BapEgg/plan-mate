package com.planmate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.planmate", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureDependencyTest {

    private static final DescribedPredicate<JavaClass> ITINERARY_INTERNAL_PACKAGE =
            new DescribedPredicate<>("reside in an itinerary package outside api") {
                @Override
                public boolean test(JavaClass javaClass) {
                    String packageName = javaClass.getPackageName();
                    return packageName.startsWith("com.planmate.itinerary.")
                            && !packageName.equals("com.planmate.itinerary.api")
                            && !packageName.startsWith("com.planmate.itinerary.api.");
                }
            };

    @ArchTest
    static final ArchRule trip_package_does_not_depend_on_itinerary_package =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.trip..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.planmate.itinerary..");

    @ArchTest
    static final ArchRule itinerary_package_does_not_depend_on_trip_persistence =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.itinerary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.planmate.trip.entity..",
                            "com.planmate.trip.repository.."
                    );

    @ArchTest
    static final ArchRule itinerary_package_does_not_depend_on_spring_messaging =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.itinerary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("org.springframework.messaging..");

    @ArchTest
    static final ArchRule itinerary_package_does_not_depend_on_realtime_package =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.itinerary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.planmate.realtime..");

    @ArchTest
    static final ArchRule realtime_package_does_not_depend_on_trip_persistence =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.realtime..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.planmate.trip.entity..",
                            "com.planmate.trip.repository.."
                    );

    @ArchTest
    static final ArchRule realtime_package_does_not_depend_on_itinerary_internal_packages =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.realtime..")
                    .should()
                    .dependOnClassesThat(ITINERARY_INTERNAL_PACKAGE);

    @ArchTest
    static final ArchRule consumers_do_not_depend_on_place_implementations =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            "com.planmate.trip..",
                            "com.planmate.recommendation..",
                            "com.planmate.itinerary.."
                    )
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.planmate.place.service..",
                            "com.planmate.place.google.."
                    );

    @ArchTest
    static final ArchRule place_controller_does_not_depend_on_google_adapter =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.place.controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.planmate.place.google..");

    @ArchTest
    static final ArchRule place_api_does_not_depend_on_google_or_spring_web_clients =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.place.api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.planmate.place.google..",
                            "org.springframework.web.client..",
                            "org.springframework.http.."
                    );

    @ArchTest
    static final ArchRule recommendation_package_does_not_depend_on_trip_package =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.recommendation..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.planmate.trip..");

    @ArchTest
    static final ArchRule itinerary_package_does_not_depend_on_recommendation_internals =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.itinerary..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.planmate.recommendation.domain..",
                            "com.planmate.recommendation.service..",
                            "com.planmate.recommendation.entity..",
                            "com.planmate.recommendation.repository.."
                    );

    @ArchTest
    static final ArchRule recommendation_api_does_not_depend_on_internal_or_technical_packages =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.recommendation.api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.planmate.recommendation.domain..",
                            "com.planmate.recommendation.service..",
                            "com.planmate.recommendation.entity..",
                            "com.planmate.recommendation.repository..",
                            "com.planmate.place..",
                            "org.springframework..",
                            "jakarta.persistence.."
                    );
}
