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
        $scope.termsAccepted = false;
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
                        .title('Trial not available')
                        .textContent('It appears that you have already enjoyed a platform subscription before. Please consider the available subscription options or kindly contact us with your special request.')
                        .ariaLabel('Trial not available')
                        .ok('OK')
                    );
            });
        };

        $scope.checkTermsAccepted = function() {
			UserService.checkTermsAccepted().then(
				function(response) {
					$scope.termsAccepted = true;
				}, function(error) {
					$scope.termsAccepted = false;
			});
        }

        $scope.acceptTerms = function() {
            UserService.acceptTerms().then(function() {
                    $scope.termsAccepted = true;
                }, function(error) {
                    $mdDialog.show(
                        $mdDialog.alert()
                        .clickOutsideToClose(true)
                        .title('Something went wrong')
                        .textContent('Oops, it seems that something went wrong. If the problem persists please contact the administrators.')
                        .ariaLabel('Something went wrong')
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
