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
    'ngMessages',
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

    var app = angular.module('ftepApp', ['app.ftepmodules', 'ngRoute', 'ngMaterial', 'ngAnimate', 'ngAria', 'ngSanitize', 'ngMessages',
                                         'ngResource', 'rzModule', 'dndLists', 'ui.bootstrap', 'openlayers-directive', 'bw.paging',
                                         'angularMoment', 'ngScrollbar', 'traverson', 'ngFileUpload']);

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
            .when('/admin', {
                templateUrl: 'views/admin/admin.html',
                controller: 'AdminCtrl',
                controllerAs: 'admin',
                resolve:{
                    "check":function($location, UserService){
                        UserService.getCurrentUser().then(function(user){
                            if(user.role != 'ADMIN'){
                                $location.path('/');  //redirect to homepage
                            }
                        });
                    }
                }
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

    app.filter('asSingular', function () {
        return function (name) {
            if (name.lastIndexOf('s') === (name.length -1)) {
                return name.substr(0, name.length-1);
            } else {
                return name;
            }
        };
    });

    app.filter("nl2br", function () {
        return function (data) {
            if (!data) {
                return data;
            }
            return data.replace(/\r/g, '<br />');
        };
    });

    app.directive('hasPermission', function() {
        return {
            link: function(scope, element, attrs, ngModel) {

                if(!attrs.hasPermission || ['READ', 'WRITE', 'ADMIN'].indexOf(attrs.hasPermission.toUpperCase() ) < 0) {
                    throw 'hasPermission must be set';
                }

                if(!attrs.hasOwnProperty('permissionSource')) {
                    throw 'For each hasPermission attribute, the permissionSource must also be set';
                }

                function checkPermission(){
                    var userPermission = 'READ'; //default user permission
                    if(attrs.permissionSource){
                        var permissionSource = JSON.parse(attrs.permissionSource);
                        userPermission = (permissionSource.accessLevel ? permissionSource.accessLevel : 'READ');
                    }

                    var allowed = false;
                    switch(attrs.hasPermission){
                        case 'READ':
                            allowed = true;
                            break;
                        case 'WRITE':
                            allowed = (['WRITE', 'ADMIN',].indexOf(userPermission.toUpperCase()) > -1);
                            break;
                        case 'ADMIN':
                            allowed = (['ADMIN'].indexOf(userPermission.toUpperCase()) > -1);
                            break;
                    }

                    //Whether an element has been requested to be disabled only. If no disable-on-check setting, hide the whole element.
                    if(attrs.hasOwnProperty('disableOnCheck')){
                        attrs.$set('disabled', !allowed);
                    }
                    else{
                        if(allowed){
                            element.show();
                        }
                        else {
                            element.hide();
                        }
                    }
                }
                checkPermission();

                // watch for element updates
                attrs.$observe('permissionSource', function() {
                    checkPermission();
                });

                element.show();

            }
          };
        });

    /** Directive for adding specific style-class based on screen size **/
    app.directive("mediaClass", ["$mdMedia", "$window", "$timeout", function($mdMedia, $window, $timeout) {
        return {
            restrict: "A",
            link: function(scope, element, attrs) {

                function checkMediaClass(){
                    var styles = attrs.mediaClass.split(',');
                    for(var i = 0; i < styles.length; i++){
                        var keyVal = styles[i].split(':');

                        //add specified class based on the screen size, the attribute values should be ordered from smallest to largest
                        if($mdMedia(keyVal[0])){
                            element.addClass(keyVal[1]);
                        }
                        // remove from elements that are in the gray area between bootstrap and material values
                        if(keyVal[0] === 'gt-md' && $mdMedia('min-width: 1199px') && $mdMedia('max-width: 1280px')){
                          element.removeClass(keyVal[1]);
                        }
                    }
                }
                checkMediaClass();

                angular.element($window).on('resize', function(){
                    $timeout(function () {
                        checkMediaClass();
                    }, 100);
                });
            }
        }
     }]);

    /** Directive for showing/hiding elements based on screen size **/
    app.directive('mediaShow', ["$mdMedia", "$window", "$timeout", function($mdMedia, $window, $timeout) {
        return {
            link: function(scope, element, attrs) {

                function checkVisibility(){
                    element.hide();

                    //if the screen size is defined in the media-show list, set the element visible at all times
                    var showList = attrs.mediaShow.split(',');
                    for(var i = 0; i < showList.length; i++){
                        if($mdMedia(showList[i])){
                            element.show();
                        }
                    }

                    // otherwise check if a special case exists for the element
                    if(element.is(':visible') === false){
                        if(attrs.mediaShowOn && attrs.mediaShowOn === "true"){
                            element.show();
                        }
                    }
                }
                checkVisibility();

                // watch for element updates
                attrs.$observe('mediaShowOn', function() {
                    checkVisibility();
                });

                angular.element($window).on('resize', function(){
                    $timeout(function () {
                        checkVisibility();
                    }, 100);
                });
            }
        }
    }]);

    return app;
});
