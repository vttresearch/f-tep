/**
 * @ngdoc overview
 * @name ftepApp
 * @description
 * # ftepApp
 *
 * Main module of the application.
 */

require(['bootstrap', 'notify']);

define([
    'ftepConfig',
    'angular',
    'ngRoute',
    'ngMaterial',
    'ngAnimate',
    'ngCookies',
    'ngSanitize',
    'ngTouch',
    'ngResource',
    'ngAria',
    'ol',
    'rzModule',
    'dndLists',
    'ngOpenlayers',
    'ngBootstrap',
    'ngPaging',
    'moment',
    'angularMoment',
    'ngScrollbar',
    'traversonAngular',
    'traversonHal',
    'ngFileUpload',
    'moduleloader'
], function (ftepConfig) {
    'use strict';

    var app = angular.module('ftepApp', ['app.ftepmodules', 'ngRoute', 'ngMaterial', 'ngAnimate', 'ngAria', 'ngSanitize', 'ngResource',
                                         'rzModule', 'dndLists', 'ui.bootstrap', 'openlayers-directive', 'bw.paging', 'angularMoment',
                                         'ngScrollbar', 'traverson', 'ngFileUpload']);

    /* jshint -W117  */
    app.constant('ftepProperties', {
        "URL_PREFIX": ftepConfig.urlPrefix,
        "URL": ftepConfig.apiUrl,
        "URLv2": ftepConfig.apiUrlv2,
        "ZOO_URL": ftepConfig.zooUrl,
        "WMS_URL": ftepConfig.wmsUrl,
        "MAPBOX_URL": "https://api.mapbox.com/styles/v1/mapbox/streets-v8/tiles/{z}/{x}/{y}?access_token=" + ftepConfig.mapboxToken
    });
    /* jshint +W117 */

    app.init = function () {
        angular.bootstrap(document, ['ftepApp']);
    };

    app.config(['$routeProvider', '$locationProvider', '$httpProvider', function ($routeProvider, $locationProvider, $httpProvider) {
        $httpProvider.defaults.withCredentials = true;
        $routeProvider
            .when('/', {
                templateUrl: 'views/explorer/explorer.html',
                controller: 'ExplorerCtrl',
                controllerAs: 'main'
            })
            .when('/developer', {
                templateUrl: 'views/developer/developer.html'
            })
            .when('/community', {
                templateUrl: 'views/community/community.html',
                controller: 'CommunityCtrl'
            })
            .when('/account', {
                templateUrl: 'views/account/account.html'
            })
            .when('/helpdesk', {
                templateUrl: 'views/helpdesk/helpdesk.html',
                controller: 'HelpdeskCtrl',
                controllerAs: 'helpdesk'
            })
            .otherwise({
                redirectTo: '/'
            });
        $locationProvider.html5Mode(false);
      }]);

    /* Custom angular-material color theme */
    app.config(['$mdThemingProvider', function ($mdThemingProvider) {
        $mdThemingProvider.theme('default')
            .primaryPalette('light-green', {
                'default': '800',
                'hue-1': '300',
                'hue-2': '900'
            })
            .accentPalette('pink', {
                'default': 'A200',
                'hue-1': 'A100',
                'hue-2': 'A700'
            })
            .warnPalette('red')
            .backgroundPalette('grey');
    }]);

    /* Set time & date format */
    app.config(['$mdDateLocaleProvider', 'moment', function ($mdDateLocaleProvider, moment) {

        $mdDateLocaleProvider.formatDate = function (date) {
            return date ? moment(date).format('DD-MM-YYYY') : '';
        };

        $mdDateLocaleProvider.parseDate = function (dateString) {
            var m = moment(dateString, 'DD-MM-YYYY', true);
            return m.isValid() ? m.toDate() : new Date(NaN);
        };
    }]);

    app.filter('bytesToGB', function () {
        return function (bytes) {
            if (isNaN(bytes) || bytes < 1) {
                return bytes;
            } else {
                return (bytes / 1073741824).toFixed(2) + ' GB';
            }
        };
    });

    return app;
});
