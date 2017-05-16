/**
 * @ngdoc function
 * @name ftepApp.controller:CommunityProjectsCtrl
 * @description
 * # CommunityProjectsCtrl
 * Controller of the ftepApp
 */

'use strict';

define(['../../../ftepmodules'], function (ftepmodules) {

    ftepmodules.controller('CommunityProjectsCtrl', ['ProjectService', 'CommonService', '$scope', function (ProjectService, CommonService, $scope) {

        /* Get stored Project details */
        $scope.projectParams = ProjectService.params.community;
        $scope.projectOwnershipFilters = ProjectService.projectOwnershipFilters;
        $scope.item = "Project";

        /* Get Projects */
        ProjectService.refreshProjects("community");

        /* Update Projects when polling */
        $scope.$on('poll.projects', function (event, data) {
            $scope.projectParams.projects = data;
        });

        $scope.getPage = function(url){
            ProjectService.getProjectsPage('community', url);
        };

        $scope.$on("$destroy", function() {
            ProjectService.stopPolling();
        });

        /* Select a Project */
        $scope.selectProject = function (item) {
            $scope.projectParams.selectedProject = item;
            ProjectService.refreshSelectedProject("community");
        };

        /* Filters */
        $scope.toggleFilters = function () {
            $scope.projectParams.displayFilters = !$scope.projectParams.displayFilters;
        };

        $scope.itemSearch = {
            searchText: $scope.projectParams.searchText
        };

        $scope.quickSearch = function (item) {
            if (item.name.toLowerCase().indexOf(
                $scope.itemSearch.searchText.toLowerCase()) > -1) {
                return true;
            }
            return false;
        };

        /* Create Project */
        $scope.createItemDialog = function ($event) {
            CommonService.createItemDialog($event, 'ProjectService', 'createProject').then(function (newProject) {
                ProjectService.refreshProjects("community", "Create");
            });
        };

        /* Edit Project */
        $scope.editItemDialog = function ($event, item) {
            CommonService.editItemDialog($event, item, 'ProjectService', 'updateProject').then(function (updatedProject) {
                ProjectService.refreshProjects("community");
            });
        };

    }]);
});
