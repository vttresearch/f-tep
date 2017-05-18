/**
 * @ngdoc function
 * @name ftepApp.controller:WorkspaceCtrl
 * @description
 * # WorkspaceCtrl
 * Controller of the ftepApp
 */
'use strict';

define(['../../../ftepmodules'], function (ftepmodules) {

    ftepmodules.controller('WorkspaceCtrl', [ '$scope', '$rootScope', '$sce', '$filter', 'JobService', 'ProductService', 'MapService', 'BasketService', 'CommonService',
                                function ($scope, $rootScope, $sce, $filter, JobService, ProductService, MapService, BasketService, CommonService) {

        $scope.serviceParams = ProductService.params.explorer;
        $scope.isWorkspaceLoading = false;

        $scope.$on('update.selectedService', function(event, service, inputs) {
            $scope.isWorkspaceLoading = true;
            $scope.serviceParams.inputValues = {};
            $scope.serviceParams.dropLists = {};

            if(inputs){
                for (var key in inputs) {
                    $scope.serviceParams.inputValues[key] = inputs[key][0]; //First value is the actual input

                    //if value has links in it, add also to dropList to show file chips
                    if(inputs[key][0].indexOf('://') > -1){
                        var list = inputs[key][0].split(',');
                        $scope.serviceParams.dropLists[key] = [];
                        for(var index in list){
                            $scope.serviceParams.dropLists[key].push({ link: list[index] });
                        }
                    }
                }
            }

            ProductService.getService(service).then(function(detailedService){
                $scope.serviceParams.selectedService = detailedService;
                $scope.isWorkspaceLoading = false;
            });
        });

        $scope.getDefaultValue = function(fieldDesc){
            return $scope.serviceParams.inputValues[fieldDesc.id] ? $scope.serviceParams.inputValues[fieldDesc.id] : fieldDesc.defaultAttrs.value;
        };

        $scope.launchProcessing = function($event) {
            var iparams={};

            for(var key in $scope.serviceParams.inputValues){
                if ($scope.serviceParams.inputValues.hasOwnProperty(key)) {
                    iparams[key] = [$scope.serviceParams.inputValues[key]];
                }
            }

            JobService.createJobConfig($scope.serviceParams.selectedService, iparams).then(function(jobConfig){
                JobService.estimateJob(jobConfig).then(function(estimation){
                    if(estimation.currentWalletBalance < estimation.estimatedCost){
                        CommonService.infoBulletin($event, 'The cost of this job exceeds Your balance. This job cannot be run.' +
                                '\nYour balance: ' + estimation.currentWalletBalance +
                                '\nCost estimation: ' + estimation.estimatedCost);
                    }
                    else {
                        var currency = ( estimation.estimatedCost === 1 ? 'coin' : 'coins' );
                        CommonService.confirm($event, 'This job will cost ' + estimation.estimatedCost + ' ' + currency + '.' +
                                '\nAre you sure you want to continue?').then(function (confirmed) {
                            if (confirmed === false) {
                                return;
                            }
                            JobService.launchJob(jobConfig, $scope.serviceParams.selectedService).then(function () {
                                JobService.refreshJobs("explorer", "Create");
                            });
                        });
                    }
                });
            });
        };

        $scope.pastePolygon = function(identifier){
            $scope.serviceParams.inputValues[identifier] = MapService.getPolygonWkt();
        };

        /** DRAG-AND-DROP FILES TO THE INPUT FIELD **/
        $scope.onDrop = function(dropObject, fieldId) {
            if($scope.serviceParams.dropLists[fieldId] === undefined){
                $scope.serviceParams.dropLists[fieldId] = [];
            }

            var file = {};

            if(dropObject && dropObject.type === 'outputs') {
                for(var i = 0; i < dropObject.selectedOutputs.length; i++){
                    file = {
                        name: dropObject.selectedOutputs[i],
                        link: dropObject.selectedOutputs[i],
                        start: dropObject.job.startTime,
                        stop: dropObject.job.endTime
                    };
                    if($scope.serviceParams.inputValues[fieldId] === undefined || $scope.serviceParams.inputValues[fieldId].indexOf(file.link) < 0){
                        $scope.serviceParams.dropLists[fieldId].push(file);
                    }
                }
                setFilesInputString(fieldId);
            }
            else if(dropObject && dropObject.type === 'results') {
                for(var j = 0; j < dropObject.selectedItems.length; j++){
                    file = {
                        name: dropObject.selectedItems[j].identifier,
                        link: dropObject.selectedItems[j].link,
                        start: dropObject.selectedItems[j].start,
                        stop: dropObject.selectedItems[j].stop,
                        bytes: dropObject.selectedItems[j].size
                    };
                    if($scope.serviceParams.inputValues[fieldId] === undefined || $scope.serviceParams.inputValues[fieldId].indexOf(file.link) < 0){
                        $scope.serviceParams.dropLists[fieldId].push(file);
                    }
                }
                setFilesInputString(fieldId);
            }
            else if(dropObject && dropObject.type === 'databasket') {
                BasketService.getDatabasketContents(dropObject.basket).then(function(files){
                    for(var i = 0; i < files.length; i++){
                        file = {
                            name: files[i].filename,
                            link: files[i]._links.self.href
                        };
                        if($scope.serviceParams.inputValues[fieldId] === undefined || $scope.serviceParams.inputValues[fieldId].indexOf(file.link) < 0){
                            $scope.serviceParams.dropLists[fieldId].push(file);
                        }
                    }
                    setFilesInputString(fieldId);
                });
                return true;
            }
            else if(dropObject && dropObject.type === 'basketItem') {
                file = {
                    name: dropObject.item.filename,
                    link: dropObject.item._links.self.href
                };
                if($scope.serviceParams.inputValues[fieldId] === undefined || $scope.serviceParams.inputValues[fieldId].indexOf(file.link) < 0){
                    $scope.serviceParams.dropLists[fieldId].push(file);
                }
                setFilesInputString(fieldId);
            }
            else {
                return false;
            }
            return true;
        };

        function setFilesInputString(fieldId){
            var pathsStr = '';
            for(var i = 0; i < $scope.serviceParams.dropLists[fieldId].length; i++){
                pathsStr += ',' + $scope.serviceParams.dropLists[fieldId][i].link;
            }
            $scope.serviceParams.inputValues[fieldId] = pathsStr.substring(1);
        }

        $scope.removeSelectedItem = function(fieldId, item){
            var index = $scope.serviceParams.dropLists[fieldId].indexOf(item);
            $scope.serviceParams.dropLists[fieldId].splice(index, 1);
            setFilesInputString(fieldId);
        };

        $scope.updateDropList = function(fieldId){
            if($scope.serviceParams.inputValues[fieldId] === undefined || $scope.serviceParams.inputValues[fieldId] === ''){
                $scope.serviceParams.dropLists[fieldId] = undefined;
            }
            else {
                var csvList = $scope.serviceParams.inputValues[fieldId].split(',');
                if($scope.serviceParams.dropLists[fieldId] === undefined){
                    $scope.serviceParams.dropLists[fieldId] = [];
                }
                var newDropList = [];
                for(var index in csvList){
                    var exists = false;
                    for(var i in $scope.serviceParams.dropLists[fieldId]){
                        if($scope.serviceParams.dropLists[fieldId][i].link === csvList[index]){
                            newDropList.push($scope.serviceParams.dropLists[fieldId][i]);
                            exists = true;
                        }
                    }
                    if(!exists && csvList[index] !== ''){
                        newDropList.push({link: csvList[index]});
                    }
                }
                $scope.serviceParams.dropLists[fieldId] = newDropList;
            }
        };

    }]);
});
