/**
* @ngdoc function
* @name ftepApp.controller:IndexCtrl
* @description
* # IndexCtrl
* Controller of the ftepApp
*/
define(['../ftepmodules'], function (ftepmodules) {
    'use strict';

    ftepmodules.controller('IndexCtrl', ['ftepProperties', '$scope', '$location', '$window', 'UserService', '$mdDialog', function (ftepProperties, $scope, $location, $window, UserService, $mdDialog) {

        $scope.ftepUrl = ftepProperties.FTEP_URL;
        $scope.sessionEnded = false;
        $scope.timeoutDismissed = false;
        $scope.subscribed = false;
        $scope.activeUser = {};

        $scope.$on('no.user', function() {
            $scope.sessionEnded = true;
        });

        $scope.$on('active.user', function(event, user) {
            if ($scope.activeUser.id && user.id) {
                if ($scope.activeUser.id != user.id) {
                    $scope.activeUser = user;
                    // User changed, check for subscription
                    $scope.checkActiveSubscription();
                }
            } else if ($scope.activeUser.id || user.id) {
                $scope.activeUser = user;
                // User logged in, check for subscription
                $scope.checkActiveSubscription();
            } else {
                $scope.activeUser = {};
                $scope.subscribed = false;
            }
        });

        $scope.hideTimeout = function() {
            $scope.sessionEnded = false;
            $scope.timeoutDismissed = true;
        };

        $scope.reloadRoute = function() {
            $window.location.reload();
        };

        $scope.checkActiveSubscription = function() {
            // Administrators do not need a subscription
            if ($scope.activeUser.role === "ADMIN") {
                $scope.subscribed = true;
            } else {
                UserService.getActiveSubscription().then(
                    function(subscription) {
                        $scope.subscribed = true;
                    }, function(error) {
                        $scope.subscribed = false;
                });
            }
        }

        $scope.startTrial = function() {
            UserService.startTrial().then(function() {
                    $scope.subscribed = true;
                }, function(error) {
                    $mdDialog.show(
                        $mdDialog.alert()
                        .clickOutsideToClose(true)
                        .title('Failed to start Trial')
                        .textContent('A trial can only be started if there are no existing subcriptions.')
                        .ariaLabel('Failed to start Trial')
                        .ok('OK')
                    );
            });
        };

        $scope.goTo = function (path) {
            $location.path(path);
        };

        $scope.version = document.getElementById("version").content;

        // Trigger a user check to ensure controllers load correctly
        UserService.checkLoginStatus();
    }]);
});
