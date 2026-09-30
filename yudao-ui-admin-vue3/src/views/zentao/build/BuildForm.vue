<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="700">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="构建 = 一次打包记录"
      description="它记录本次完成的需求与解决的 Bug（都是逗号列表），缺陷的「解决版本」存的就是构建编号。集成构建用「包含构建」把多个子构建合并，execution 固定为 0、分支取子构建的并集。"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item label="集成构建" prop="integrated">
        <el-switch v-model="formData.integrated" active-text="是（合并多个子构建）" inactive-text="否（单一构建）" />
      </el-form-item>

      <el-form-item v-if="formData.integrated" label="包含构建" prop="buildIds">
        <el-select v-model="formData.buildIds" multiple filterable placeholder="请选择要合并的子构建" class="w-full">
          <el-option v-for="b in candidateBuilds" :key="b.id" :label="`${b.name} (${b.date})`" :value="b.id!" />
        </el-select>
        <div class="text-12px text-gray-400">
          集成构建的分支由子构建分支自动合并；子构建不能再是集成构建
        </div>
      </el-form-item>

      <el-row>
        <el-col :span="12">
          <el-form-item label="所属产品" prop="product">
            <el-select v-model="formData.product" placeholder="请选择产品" filterable class="w-full"
                       :disabled="formData.integrated || formType === 'update' || isChild"
                       @change="handleProductChange">
              <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="branchVisible && !formData.integrated" :label="branchLabel" prop="branches">
            <el-select v-model="formData.branches" multiple collapse-tags placeholder="请选择" class="w-full">
              <el-option label="主干" :value="0" />
              <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="所属项目" prop="project">
            <el-select v-model="formData.project" placeholder="请选择项目" clearable filterable class="w-full">
              <el-option v-for="p in projectList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属执行" prop="execution">
            <el-select v-model="formData.execution" placeholder="请选择执行" clearable filterable class="w-full"
                       :disabled="formData.integrated || isChild">
              <el-option v-for="e in executionList" :key="e.id" :label="e.name" :value="e.id!" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="构建名称" prop="name">
            <el-input v-model="formData.name" placeholder="如 V1.0-beta1" maxlength="150" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="打包日期" prop="date">
            <el-date-picker v-model="formData.date" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="构建者" prop="builder">
            <el-input v-model="formData.builder" placeholder="账号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="下载地址" prop="filePath">
            <el-input v-model="formData.filePath" placeholder="http://..." />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="源代码地址" prop="scmPath">
        <el-input v-model="formData.scmPath" placeholder="git@..." />
      </el-form-item>

      <el-form-item label="描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="3" placeholder="请输入描述" />
      </el-form-item>

      <el-alert
        v-if="isChild"
        type="warning"
        :closable="false"
        show-icon
        title="该构建已被集成构建或发布引用"
        description="被引用的构建不能修改所属产品、所属执行与包含构建（禅道同样限制）。"
      />
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as BuildApi from '@/api/zentao/build'
import * as ProductApi from '@/api/zentao/product'
import * as BranchApi from '@/api/zentao/branch'
import * as ProjectApi from '@/api/zentao/project'
import * as ExecutionApi from '@/api/zentao/execution'

defineOptions({ name: 'ZentaoBuildForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const projectList = ref<ProjectApi.ProjectVO[]>([])
const executionList = ref<ExecutionApi.ExecutionVO[]>([])
const candidateBuilds = ref<BuildApi.BuildVO[]>([])
const isChild = ref(false)

const formData = ref<BuildApi.BuildVO>({})

const currentProduct = computed(() => productList.value.find((p) => p.id === formData.value.product))
const branchVisible = computed(
  () => currentProduct.value?.type === 'branch' || currentProduct.value?.type === 'platform'
)
const branchLabel = computed(() => (currentProduct.value?.type === 'platform' ? '平台' : '分支'))

const formRules = reactive({
  name: [{ required: true, message: '构建名称不能为空', trigger: 'blur' }],
  date: [{ required: true, message: '打包日期不能为空', trigger: 'change' }],
  builder: [{ required: true, message: '构建者不能为空', trigger: 'blur' }],
  buildIds: [
    {
      validator: (_r: any, _v: any, cb: any) =>
        !formData.value.integrated || (formData.value.buildIds || []).length > 0
          ? cb()
          : cb(new Error('集成构建必须选择至少一个子构建')),
      trigger: 'change'
    }
  ]
})

const handleProductChange = async () => {
  formData.value.branches = branchVisible.value ? [] : [0]
  await loadBranchAndBuilds()
}

const loadBranchAndBuilds = async () => {
  if (!formData.value.product) {
    branchList.value = []
    candidateBuilds.value = []
    return
  }
  if (branchVisible.value) {
    branchList.value = await BranchApi.getBranchListByProduct(formData.value.product)
  } else {
    branchList.value = []
  }
  // 候选子构建：同产品下的单一构建（集成构建不能再被集成）
  candidateBuilds.value = (await BuildApi.getBuildListByProduct(formData.value.product)).filter(
    (b) => !b.integrated && b.id !== formData.value.id
  )
}

const open = async (type: string, id?: number, productId?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建构建' : '编辑构建'
  resetForm()
  productList.value = await ProductApi.getProductSimpleList()
  projectList.value = await ProjectApi.getProjectSimpleList()
  const execPage = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
  executionList.value = execPage.list

  if (id) {
    formLoading.value = true
    try {
      const data = await BuildApi.getBuild(id)
      formData.value = {
        ...data,
        branches: (data.branch || '0').split(',').map((x) => Number(x.trim())),
        buildIds: (data.builds || '')
          .split(',')
          .map((x) => x.trim())
          .filter(Boolean)
          .map(Number)
      }
      isChild.value = !!data.child
      await loadBranchAndBuilds()
    } finally {
      formLoading.value = false
    }
  } else if (productId) {
    formData.value.product = productId
    await loadBranchAndBuilds()
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    const payload = { ...formData.value }
    if (formType.value === 'create') {
      await BuildApi.createBuild(payload)
      message.success(t('common.createSuccess'))
    } else {
      await BuildApi.updateBuild(payload)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    integrated: false,
    branches: [0],
    buildIds: [],
    name: '',
    builder: 'admin',
    scmPath: '',
    filePath: '',
    desc: ''
  }
  isChild.value = false
  branchList.value = []
  candidateBuilds.value = []
  formRef.value?.resetFields()
}
</script>
